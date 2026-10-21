package com.chalkak.backend.aireview.infrastructure.infra.openai;

import static com.chalkak.backend.aireview.service.AiReviewGenerationException.FailureType.BAD_REQUEST;
import static com.chalkak.backend.aireview.service.AiReviewGenerationException.FailureType.INCOMPLETE;
import static com.chalkak.backend.aireview.service.AiReviewGenerationException.FailureType.INVALID_OUTPUT;
import static com.chalkak.backend.aireview.service.AiReviewGenerationException.FailureType.QUOTA_EXCEEDED;
import static com.chalkak.backend.aireview.service.AiReviewGenerationException.FailureType.RATE_LIMITED;
import static com.chalkak.backend.aireview.service.AiReviewGenerationException.FailureType.REFUSED;
import static com.chalkak.backend.aireview.service.AiReviewGenerationException.FailureType.TIMEOUT;
import static com.chalkak.backend.aireview.service.AiReviewGenerationException.FailureType.UPSTREAM_ERROR;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.chalkak.backend.aireview.domain.AiPromptDraft;
import com.chalkak.backend.aireview.domain.AiPromptParameters;
import com.chalkak.backend.aireview.domain.PhotoReviewOutput;
import com.chalkak.backend.aireview.service.AiReviewGeneration.Attempt;
import com.chalkak.backend.aireview.service.AiReviewGeneration.Usage;
import com.chalkak.backend.aireview.service.AiReviewGeneration;
import com.chalkak.backend.aireview.service.AiReviewGenerationException.FailureType;
import com.chalkak.backend.aireview.service.AiReviewGenerationException;
import com.chalkak.backend.aireview.service.AiReviewGenerationRequest;
import com.chalkak.backend.aireview.service.AiReviewGenerator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * OpenAI Responses API의 Structured Outputs로 사진 리뷰를 생성한다.
 *
 * <p>
 * 요청·응답 본문과 API 키, 이미지 주소는 로그나 예외 메시지에 남기지 않는다.
 */
public final class OpenAiResponsesReviewGenerator implements AiReviewGenerator {

    private static final String MESSAGE_TYPE = "message";
    private static final String OUTPUT_TEXT_TYPE = "output_text";
    private static final String REFUSAL_TYPE = "refusal";
    private static final String STATUS_INCOMPLETE = "incomplete";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_FAILED = "failed";
    // 429 등 HTTP 오류 응답이 알려 주는 최소 대기 시간. 밀리초 헤더가 더 정확하므로 먼저 읽는다.
    private static final String RETRY_AFTER_MS_HEADER = "retry-after-ms";
    private static final String RETRY_AFTER_HEADER = "retry-after";
    private static final String RATE_LIMIT_ERROR_CODE = "rate_limit_exceeded";
    // status=failed의 error.code 중 정책 위반은 같은 입력을 다시 보내도 거절되므로 재시도 대상이 아니다.
    private static final Set<String> REFUSED_ERROR_CODES = Set.of(
            "image_content_policy_violation",
            "bio_policy",
            "misalignment_policy_violation"
    );
    // 프롬프트나 이미지 자체가 잘못된 경우라 재시도해도 같은 결과다. 목록에 없는 코드는 일시 장애로 본다.
    private static final Set<String> BAD_REQUEST_ERROR_CODES = Set.of(
            "invalid_prompt",
            "data_residency_mismatch",
            "invalid_image",
            "invalid_image_format",
            "invalid_base64_image",
            "invalid_image_url",
            "image_too_large",
            "image_too_small",
            "image_parse_error",
            "invalid_image_mode",
            "image_file_too_large",
            "unsupported_image_media_type",
            "empty_image_file",
            "image_file_not_found"
    );
    // 429여도 code 또는 type이 결제·한도 소진이면 기다려도 풀리지 않아 재시도 대상이 아니다.
    private static final Set<String> QUOTA_ERROR_CODES = Set.of(
            "insufficient_quota",
            "credit_balance_exhausted",
            "organization_spend_limit_exceeded",
            "project_spend_limit_exceeded",
            "organization_usage_limit_exceeded"
    );

    // OpenAI Structured Outputs 전용 스키마라 도메인이 아닌 이 어댑터가 정의한다.
    // strict 모드는 모든 필드를 required로 두고 additionalProperties를 막아야 한다.
    private static final Map<String, Object> PHOTO_REVIEW_SCHEMA = Map.of(
            "type",
            "object",
            "additionalProperties",
            false,
            "required",
            List.of(
                    "composition",
                    "theme_expression",
                    "feedback"
            ),
            "properties",
            Map.of(
                    "composition",
                    Map.of(
                            "type",
                            "number",
                            "minimum",
                            PhotoReviewOutput.minScore(),
                            "maximum",
                            PhotoReviewOutput.maxScore()
                    ),
                    "theme_expression",
                    Map.of(
                            "type",
                            "number",
                            "minimum",
                            PhotoReviewOutput.minScore(),
                            "maximum",
                            PhotoReviewOutput.maxScore()
                    ),
                    "feedback",
                    Map.of("type", "string")
            )
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String apiKey;
    private final URI responsesUri;
    private final List<String> allowedModels;
    private final Duration readTimeout;

    public OpenAiResponsesReviewGenerator(
            RestClient restClient,
            ObjectMapper objectMapper,
            Clock clock,
            OpenAiProperties properties
    ) {
        this.restClient = Objects.requireNonNull(restClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
        this.apiKey = properties.apiKey();
        this.allowedModels = properties.allowedModels();
        this.readTimeout = properties.readTimeout();
        this.responsesUri = URI.create(
                properties.baseUrl().toString().replaceAll("/+$", "") + "/responses");
    }

    @Override
    public AiReviewGeneration generate(AiReviewGenerationRequest request) {
        ResponsesRequest body = createRequestBody(request);
        Instant startedAt = clock.instant();

        String responseBody = send(body, startedAt);

        Duration latency = Duration.between(startedAt, clock.instant());
        return parse(
                responseBody,
                latency,
                request.draft().parameters().distinctivePerspectiveThreshold()
        );
    }

    @Override
    public boolean supportsModel(String model) {
        return model != null && allowedModels.contains(model);
    }

    private ResponsesRequest createRequestBody(AiReviewGenerationRequest request) {
        AiPromptDraft draft = request.draft();
        AiPromptParameters parameters = draft.parameters();
        String userText = draft.renderUserPrompt(request.topicTitle());
        Reasoning reasoning = parameters.reasoningEffort() == null
                ? null
                : new Reasoning(lowerCase(parameters.reasoningEffort()));

        return new ResponsesRequest(
                draft.model(),
                draft.systemPrompt(),
                List.of(new InputMessage("user", List.of(
                        new InputText("input_text", userText),
                        new InputImage(
                                "input_image",
                                request.imageUrl(),
                                lowerCase(parameters.imageDetail())
                        )))),
                new TextOptions(new JsonSchemaFormat(
                        "json_schema",
                        "photo_review_v" + PhotoReviewOutput.schemaVersion(),
                        true,
                        PHOTO_REVIEW_SCHEMA
                )),
                false,
                parameters.maxOutputTokens(),
                reasoning
        );
    }

    private String send(ResponsesRequest body, Instant startedAt) {
        try {
            return restClient.post()
                    .uri(responsesUri)
                    .header(AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(
                            status -> !status.is2xxSuccessful(),
                            (httpRequest, response) -> {
                                FailureType failureType = failureTypeOf(response);
                                throw retryAfterOf(failureType, response)
                                        .map(retryAfter -> AiReviewGenerationException
                                                .withRetryAfter(failureType, retryAfter))
                                        .orElseGet(() -> AiReviewGenerationException
                                                .of(failureType));
                            })
                    .body(String.class);
        } catch (ResourceAccessException exception) {
            throw AiReviewGenerationException.withCause(
                    transportFailureType(exception, startedAt),
                    exception
            );
        } catch (RestClientException exception) {
            throw AiReviewGenerationException.of(transportFailureType(exception, startedAt));
        }
    }

    // Spring의 읽기 제한 시간은 본문을 읽는 중이면 스트림만 닫아 원인에 시간 초과 예외가 남지 않으므로 경과 시간도 본다.
    private FailureType transportFailureType(RestClientException exception, Instant startedAt) {
        boolean deadlineExceeded = Duration.between(startedAt, clock.instant())
                .compareTo(readTimeout) >= 0;
        return isTimeout(exception) || deadlineExceeded ? TIMEOUT : UPSTREAM_ERROR;
    }

    private AiReviewGeneration parse(
            String responseBody,
            Duration latency,
            double threshold
    ) {
        ResponsesResponse response = readResponse(responseBody);
        Attempt attempt = new Attempt(
                response.toUsage(),
                latency,
                response.id()
        );
        if (STATUS_INCOMPLETE.equals(response.status())) {
            throw AiReviewGenerationException.withAttempt(INCOMPLETE, attempt);
        }
        if (STATUS_FAILED.equals(response.status())) {
            throw AiReviewGenerationException.withAttempt(failureTypeOf(response.error()), attempt);
        }
        if (!STATUS_COMPLETED.equals(response.status())) {
            throw AiReviewGenerationException.withAttempt(UPSTREAM_ERROR, attempt);
        }
        if (response.hasNullElement()) {
            throw AiReviewGenerationException.withAttempt(INVALID_OUTPUT, attempt);
        }

        List<ContentPart> parts = response.contentParts();
        if (parts.stream().anyMatch(part -> REFUSAL_TYPE.equals(part.type()))) {
            throw AiReviewGenerationException.withAttempt(REFUSED, attempt);
        }
        String outputText = parts.stream()
                .filter(part -> OUTPUT_TEXT_TYPE.equals(part.type()))
                .map(ContentPart::text)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(
                        () -> AiReviewGenerationException.withAttempt(INVALID_OUTPUT, attempt));

        PhotoReviewOutput output = readOutput(outputText)
                .orElseThrow(
                        () -> AiReviewGenerationException.withAttempt(INVALID_OUTPUT, attempt));
        return new AiReviewGeneration(
                output.toResult(threshold),
                output,
                outputText,
                attempt
        );
    }

    private ResponsesResponse readResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw AiReviewGenerationException.of(INVALID_OUTPUT);
        }
        try {
            ResponsesResponse response = objectMapper.readValue(responseBody,
                    ResponsesResponse.class);
            if (response == null) {
                throw AiReviewGenerationException.of(INVALID_OUTPUT);
            }
            return response;
        } catch (JacksonException exception) {
            throw AiReviewGenerationException.of(INVALID_OUTPUT);
        }
    }

    /**
     * 모델 출력의 JSON 타입을 직접 확인한다. Jackson의 기본 변환은 "7" 같은 문자열도 숫자로 받아들일 수 있어서 두 점수 모두
     * 변환 없이 숫자 노드만 허용한다.
     */
    private Optional<PhotoReviewOutput> readOutput(String outputText) {
        try {
            PhotoReviewPayload payload = objectMapper.readValue(outputText,
                    PhotoReviewPayload.class);
            if (payload == null
                    || payload.composition() == null
                    || !payload.composition().isNumber()
                    || payload.themeExpression() == null
                    || !payload.themeExpression().isNumber()
                    || payload.feedback() == null
                    || !payload.feedback().isString()) {
                return Optional.empty();
            }
            return Optional.of(new PhotoReviewOutput(
                    payload.composition().doubleValue(),
                    payload.themeExpression().doubleValue(),
                    payload.feedback().stringValue()
            ));
        } catch (JacksonException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private FailureType failureTypeOf(ErrorBody error) {
        if (error == null || error.code() == null) {
            return UPSTREAM_ERROR;
        }
        String code = error.code();
        if (RATE_LIMIT_ERROR_CODE.equals(code)) {
            return RATE_LIMITED;
        }
        if (REFUSED_ERROR_CODES.contains(code)) {
            return REFUSED;
        }
        if (BAD_REQUEST_ERROR_CODES.contains(code)) {
            return BAD_REQUEST;
        }
        return UPSTREAM_ERROR;
    }

    private FailureType failureTypeOf(ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        if (status.value() == 429) {
            return isQuotaExhausted(readError(response)) ? QUOTA_EXCEEDED : RATE_LIMITED;
        }
        if (status.value() == 408) {
            return TIMEOUT;
        }
        if (status.value() == 409) {
            return UPSTREAM_ERROR;
        }
        if (status.is4xxClientError()) {
            return BAD_REQUEST;
        }
        return UPSTREAM_ERROR;
    }

    /**
     * 재시도하지 않는 유형은 기다려도 소용없으므로 헤더가 있어도 싣지 않는다. 날짜 형식의 retry-after는 OpenAI가 보내지 않아
     * 읽지 않는다.
     */
    private Optional<Duration> retryAfterOf(FailureType failureType, ClientHttpResponse response) {
        if (!failureType.isRetryable()) {
            return Optional.empty();
        }
        HttpHeaders headers = response.getHeaders();
        return parseDelay(headers.getFirst(RETRY_AFTER_MS_HEADER), ChronoUnit.MILLIS)
                .or(() -> parseDelay(headers.getFirst(RETRY_AFTER_HEADER), ChronoUnit.SECONDS));
    }

    private Optional<Duration> parseDelay(String value, ChronoUnit unit) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            long amount = Long.parseLong(value.strip());
            return amount < 0 ? Optional.empty() : Optional.of(Duration.of(amount, unit));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    /**
     * 429의 원인을 가르는 error.code·type만 읽는다. 본문이 비었거나 읽을 수 없으면 비어 있는 값으로 본다.
     */
    private ErrorBody readError(ClientHttpResponse response) {
        try {
            ErrorEnvelope envelope = objectMapper.readValue(response.getBody(),
                    ErrorEnvelope.class);
            return envelope == null || envelope.error() == null
                    ? new ErrorBody(null, null)
                    : envelope.error();
        } catch (JacksonException | IOException exception) {
            return new ErrorBody(null, null);
        }
    }

    private boolean isQuotaExhausted(ErrorBody error) {
        return (error.code() != null && QUOTA_ERROR_CODES.contains(error.code()))
                || (error.type() != null && QUOTA_ERROR_CODES.contains(error.type()));
    }

    private boolean isTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private String lowerCase(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private record ResponsesRequest(
            String model,
            String instructions,
            List<InputMessage> input,
            TextOptions text,
            boolean store,
            @JsonProperty("max_output_tokens") int maxOutputTokens,
            @JsonInclude(JsonInclude.Include.NON_NULL) Reasoning reasoning
    ) {
    }

    private record InputMessage(String role, Object content) {
    }

    private record InputText(String type, String text) {
    }

    private record InputImage(
            String type,
            @JsonProperty("image_url") String imageUrl,
            String detail
    ) {
    }

    private record TextOptions(JsonSchemaFormat format) {
    }

    private record JsonSchemaFormat(
            String type,
            String name,
            boolean strict,
            Map<String, Object> schema
    ) {
    }

    private record Reasoning(String effort) {
    }

    private record ErrorEnvelope(ErrorBody error) {
    }

    private record ErrorBody(String code, String type) {
    }

    private record ResponsesResponse(
            String id,
            String status,
            List<OutputItem> output,
            UsageBody usage,
            ErrorBody error
    ) {

        private boolean hasNullElement() {
            if (output == null) {
                return false;
            }
            return output.stream().anyMatch(item -> item == null
                    || (item.content() != null
                            && item.content().stream().anyMatch(Objects::isNull)));
        }

        private Usage toUsage() {
            if (usage == null) {
                return new Usage(
                        null,
                        null,
                        null,
                        null
                );
            }
            return new Usage(
                    usage.inputTokens(),
                    usage.inputTokensDetails() == null
                            ? null
                            : usage.inputTokensDetails().cachedTokens(),
                    usage.outputTokens(),
                    usage.outputTokensDetails() == null
                            ? null
                            : usage.outputTokensDetails().reasoningTokens()
            );
        }

        private List<ContentPart> contentParts() {
            if (output == null) {
                return List.of();
            }
            return output.stream()
                    .filter(item -> MESSAGE_TYPE.equals(item.type()) && item.content() != null)
                    .flatMap(item -> item.content().stream())
                    .toList();
        }
    }

    private record OutputItem(String type, List<ContentPart> content) {
    }

    private record ContentPart(
            String type,
            String text,
            String refusal
    ) {
    }

    private record UsageBody(
            @JsonProperty("input_tokens") Integer inputTokens,
            @JsonProperty("input_tokens_details") InputTokensDetails inputTokensDetails,
            @JsonProperty("output_tokens") Integer outputTokens,
            @JsonProperty("output_tokens_details") OutputTokensDetails outputTokensDetails
    ) {
    }

    private record InputTokensDetails(@JsonProperty("cached_tokens") Integer cachedTokens) {
    }

    private record OutputTokensDetails(@JsonProperty("reasoning_tokens") Integer reasoningTokens) {
    }

    private record PhotoReviewPayload(
            JsonNode composition,
            @JsonProperty("theme_expression") JsonNode themeExpression,
            JsonNode feedback
    ) {
    }
}
