package com.chalkak.backend.aireview.infrastructure.infra.openai;

import static com.chalkak.backend.aireview.service.AiReviewGenerationException.FailureType.BAD_REQUEST;
import static com.chalkak.backend.aireview.service.AiReviewGenerationException.FailureType.INCOMPLETE;
import static com.chalkak.backend.aireview.service.AiReviewGenerationException.FailureType.INVALID_OUTPUT;
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
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * OpenAI Responses API의 Structured Outputs로 사진 리뷰를 생성한다.
 *
 * <p>요청·응답 본문과 API 키, 이미지 주소는 로그나 예외 메시지에 남기지 않는다.
 */
public final class OpenAiResponsesReviewGenerator implements AiReviewGenerator {

    private static final String MESSAGE_TYPE = "message";
    private static final String OUTPUT_TEXT_TYPE = "output_text";
    private static final String REFUSAL_TYPE = "refusal";
    private static final String STATUS_INCOMPLETE = "incomplete";
    private static final String STATUS_COMPLETED = "completed";

    // OpenAI Structured Outputs 전용 스키마라 도메인이 아닌 이 어댑터가 정의한다.
    // strict 모드는 모든 필드를 required로 두고 additionalProperties를 막아야 한다.
    private static final Map<String, Object> PHOTO_REVIEW_SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("composition", "theme_expression", "feedback"),
            "properties", Map.of(
                    "composition", Map.of(
                            "type", "number",
                            "minimum", PhotoReviewOutput.minScore(),
                            "maximum", PhotoReviewOutput.maxScore()),
                    "theme_expression", Map.of(
                            "type", "number",
                            "minimum", PhotoReviewOutput.minScore(),
                            "maximum", PhotoReviewOutput.maxScore()),
                    "feedback", Map.of("type", "string")));

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String apiKey;
    private final URI responsesUri;

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
        this.responsesUri = URI.create(
                properties.baseUrl().toString().replaceAll("/+$", "") + "/responses");
    }

    @Override
    public AiReviewGeneration generate(AiReviewGenerationRequest request) {
        ResponsesRequest body = createRequestBody(request);
        Instant startedAt = clock.instant();

        String responseBody = send(body);

        Duration latency = Duration.between(startedAt, clock.instant());
        return parse(responseBody, latency, request.draft().parameters().distinctivePerspectiveThreshold());
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
                                lowerCase(parameters.imageDetail()))))),
                new TextOptions(new JsonSchemaFormat(
                        "json_schema",
                        "photo_review_v" + PhotoReviewOutput.schemaVersion(),
                        true,
                        PHOTO_REVIEW_SCHEMA)),
                false,
                parameters.maxOutputTokens(),
                reasoning);
    }

    private String send(ResponsesRequest body) {
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
                                throw new AiReviewGenerationException(
                                        failureTypeOf(response.getStatusCode()));
                            })
                    .body(String.class);
        } catch (ResourceAccessException exception) {
            throw new AiReviewGenerationException(
                    isTimeout(exception) ? TIMEOUT : UPSTREAM_ERROR,
                    exception);
        } catch (RestClientException exception) {
            throw new AiReviewGenerationException(UPSTREAM_ERROR);
        }
    }

    private AiReviewGeneration parse(String responseBody, Duration latency, double threshold) {
        ResponsesResponse response = readResponse(responseBody);
        Attempt attempt = new Attempt(response.toUsage(), latency, response.id());
        if (STATUS_INCOMPLETE.equals(response.status())) {
            throw new AiReviewGenerationException(INCOMPLETE, attempt);
        }
        if (!STATUS_COMPLETED.equals(response.status())) {
            throw new AiReviewGenerationException(UPSTREAM_ERROR, attempt);
        }

        List<ContentPart> parts = response.contentParts();
        if (parts.stream().anyMatch(part -> REFUSAL_TYPE.equals(part.type()))) {
            throw new AiReviewGenerationException(REFUSED, attempt);
        }
        String outputText = parts.stream()
                .filter(part -> OUTPUT_TEXT_TYPE.equals(part.type()))
                .map(ContentPart::text)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new AiReviewGenerationException(INVALID_OUTPUT, attempt));

        PhotoReviewOutput output = readOutput(outputText)
                .orElseThrow(() -> new AiReviewGenerationException(INVALID_OUTPUT, attempt));
        return new AiReviewGeneration(
                output.toResult(threshold),
                output,
                attempt);
    }

    private ResponsesResponse readResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new AiReviewGenerationException(INVALID_OUTPUT);
        }
        try {
            return objectMapper.readValue(responseBody, ResponsesResponse.class);
        } catch (JacksonException exception) {
            throw new AiReviewGenerationException(INVALID_OUTPUT);
        }
    }

    /**
     * 모델 출력의 JSON 타입을 직접 확인한다. Jackson의 기본 변환은 "7" 같은 문자열도 숫자로 받아들일 수 있어서
     * 두 점수 모두 변환 없이 숫자 노드만 허용한다.
     */
    private Optional<PhotoReviewOutput> readOutput(String outputText) {
        try {
            PhotoReviewPayload payload = objectMapper.readValue(outputText, PhotoReviewPayload.class);
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
                    payload.feedback().stringValue()));
        } catch (JacksonException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private FailureType failureTypeOf(HttpStatusCode status) {
        if (status.value() == 429) {
            return RATE_LIMITED;
        }
        if (status.is4xxClientError()) {
            return BAD_REQUEST;
        }
        return UPSTREAM_ERROR;
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

    private record ResponsesResponse(
            String id,
            String status,
            List<OutputItem> output,
            UsageBody usage
    ) {

        private Usage toUsage() {
            if (usage == null) {
                return new Usage(null, null, null, null);
            }
            return new Usage(
                    usage.inputTokens(),
                    usage.inputTokensDetails() == null ? null : usage.inputTokensDetails().cachedTokens(),
                    usage.outputTokens(),
                    usage.outputTokensDetails() == null
                            ? null
                            : usage.outputTokensDetails().reasoningTokens());
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

    private record ContentPart(String type, String text, String refusal) {
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
