package com.chalkak.backend.aireview.infrastructure.infra.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.chalkak.backend.aireview.domain.AiPromptDraft;
import com.chalkak.backend.aireview.domain.AiPromptParameters.ImageDetail;
import com.chalkak.backend.aireview.domain.AiPromptParameters.ReasoningEffort;
import com.chalkak.backend.aireview.domain.AiPromptParameters;
import com.chalkak.backend.aireview.domain.AiPromptType;
import com.chalkak.backend.aireview.domain.AiReviewResult;
import com.chalkak.backend.aireview.service.AiReviewGeneration.Attempt;
import com.chalkak.backend.aireview.service.AiReviewGeneration.Usage;
import com.chalkak.backend.aireview.service.AiReviewGeneration;
import com.chalkak.backend.aireview.service.AiReviewGenerationException.FailureType;
import com.chalkak.backend.aireview.service.AiReviewGenerationException;
import com.chalkak.backend.aireview.service.AiReviewGenerationRequest;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class OpenAiResponsesReviewGeneratorTest {

    private static final String RESPONSES_URL = "https://openai.test/v1/responses";
    private static final String API_KEY = "sk-test-secret";
    private static final String IMAGE_URL = "https://cdn.test/photo.jpg";
    private static final String OK_OUTPUT =
            "{\"compositionScore\":8.72,\"distinctivePerspective\":true,\"feedback\":\"구도가 좋습니다.\"}";

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private MockRestServiceServer server;
    private Clock clock;
    private OpenAiResponsesReviewGenerator generator;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        clock = mock(Clock.class);
        Instant start = Instant.parse("2026-10-19T00:00:00Z");
        when(clock.instant()).thenReturn(start, start.plusMillis(1234));
        OpenAiProperties properties = new OpenAiProperties(
                API_KEY,
                URI.create("https://openai.test/v1/"),
                Duration.ofSeconds(5),
                Duration.ofSeconds(60));
        generator = new OpenAiResponsesReviewGenerator(
                builder.build(),
                objectMapper,
                clock,
                properties);
    }

    @AfterEach
    void tearDown() {
        server.verify();
    }

    @Test
    @DisplayName("generate_요청을_보내면_OpenAI Responses 계약대로 본문과 인증 헤더를 구성한다")
    void generate_request_matchesResponsesContract() {
        // Given
        String expectedBody = """
                {
                  "model": "gpt-5-mini",
                  "instructions": "사진을 평가한다.",
                  "input": [
                    {"role": "user", "content": [
                      {"type": "input_text", "text": "주제: 노을, 다시 노을"},
                      {"type": "input_image", "image_url": "https://cdn.test/photo.jpg",
                       "detail": "low"}
                    ]}
                  ],
                  "text": {"format": {
                    "type": "json_schema",
                    "name": "photo_review_v1",
                    "strict": true,
                    "schema": {
                      "type": "object",
                      "additionalProperties": false,
                      "required": ["compositionScore", "distinctivePerspective", "feedback"],
                      "properties": {
                        "compositionScore": {"type": "number", "minimum": 0, "maximum": 10},
                        "distinctivePerspective": {"type": "boolean"},
                        "feedback": {"type": "string"}
                      }
                    }
                  }},
                  "store": false,
                  "max_output_tokens": 700
                }
                """;
        server.expect(requestTo(RESPONSES_URL))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(expectedBody, JsonCompareMode.STRICT))
                .andRespond(withSuccess(successBody(OK_OUTPUT), MediaType.APPLICATION_JSON));

        // When & Then
        generator.generate(request(ImageDetail.LOW, 700, null));
    }

    @Test
    @DisplayName("generate_reasoningEffort가 있으면_reasoning을 보내고 없으면_생략한다")
    void generate_reasoningEffort_isSentOnlyWhenSet() {
        // Given
        server.expect(requestTo(RESPONSES_URL))
                .andExpect(jsonPath("$.reasoning").doesNotExist())
                .andRespond(withSuccess(successBody(OK_OUTPUT), MediaType.APPLICATION_JSON));
        server.expect(requestTo(RESPONSES_URL))
                .andExpect(jsonPath("$.reasoning.effort").value("medium"))
                .andRespond(withSuccess(successBody(OK_OUTPUT), MediaType.APPLICATION_JSON));
        when(clock.instant()).thenReturn(Instant.EPOCH);

        // When & Then
        generator.generate(request(ImageDetail.AUTO, 500, null));
        generator.generate(request(ImageDetail.AUTO, 500, ReasoningEffort.MEDIUM));
    }

    @Test
    @DisplayName("generate_성공 응답이면_결과와 토큰 사용량 응답 ID 지연시간을 반환한다")
    void generate_success_returnsResultAndUsage() {
        // Given
        server.expect(requestTo(RESPONSES_URL))
                .andRespond(withSuccess(successBody(OK_OUTPUT), MediaType.APPLICATION_JSON));

        // When
        AiReviewGeneration generation = generator.generate(request(ImageDetail.AUTO, 500, null));

        // Then
        AiReviewResult result = generation.result();
        assertThat(result.overallScore()).isEqualTo(8.7);
        assertThat(result.criteria()).containsExactly(
                new AiReviewResult.Criterion("COMPOSITION", "구도", 8.7));
        assertThat(result.distinctivePerspective()).isTrue();
        assertThat(result.feedback()).isEqualTo("구도가 좋습니다.");
        assertThat(generation.attempt().usage()).isEqualTo(new Usage(1200, 300, 85, 40));
        assertThat(generation.attempt().responseId()).isEqualTo("resp_123");
        assertThat(generation.attempt().latency()).isEqualTo(Duration.ofMillis(1234));
    }

    @Test
    @DisplayName("generate_usage가 없어도_리뷰는 성공하고 토큰 수는 알 수 없음(null)이다")
    void generate_missingUsage_returnsNullTokens() {
        // Given
        respondOk(objectMapper.writeValueAsString(Map.of(
                "id", "resp_123",
                "status", "completed",
                "output", List.of(Map.of(
                        "type", "message",
                        "content", List.of(Map.of("type", "output_text", "text", OK_OUTPUT)))))));

        // When
        AiReviewGeneration generation = generator.generate(request(ImageDetail.AUTO, 500, null));

        // Then
        assertThat(generation.result().overallScore()).isEqualTo(8.7);
        assertThat(generation.attempt().usage()).isEqualTo(new Usage(null, null, null, null));
    }

    @Test
    @DisplayName("generate_usage에 0이 오면_0 그대로 보고한다")
    void generate_zeroUsage_reportsZero() {
        // Given
        respondOk(objectMapper.writeValueAsString(Map.of(
                "id", "resp_123",
                "status", "completed",
                "output", List.of(Map.of(
                        "type", "message",
                        "content", List.of(Map.of("type", "output_text", "text", OK_OUTPUT)))),
                "usage", Map.of("input_tokens", 0, "output_tokens", 0))));

        // When
        AiReviewGeneration generation = generator.generate(request(ImageDetail.AUTO, 500, null));

        // Then
        assertThat(generation.attempt().usage()).isEqualTo(new Usage(0, null, 0, null));
    }

    @Test
    @DisplayName("generate_거절과 출력 텍스트가 함께 오면_REFUSED로 실패한다")
    void generate_refusalWithOutputText_failsWithRefused() {
        // Given
        respondOk(responseBody("completed", List.of(Map.of(
                "type", "message",
                "content", List.of(
                        Map.of("type", "output_text", "text", OK_OUTPUT),
                        Map.of("type", "refusal", "refusal", "거절"))))));

        // When & Then
        assertFailure(FailureType.REFUSED, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[{\"type\":\"message\",\"content\":[]}]",
            "[{\"type\":\"reasoning\"}]"})
    @DisplayName("generate_출력 텍스트가 없으면_INVALID_OUTPUT으로 실패한다")
    void generate_noOutputText_failsWithInvalidOutput(String output) {
        // Given
        respondOk("{\"id\":\"resp_1\",\"status\":\"completed\",\"output\":" + output + "}");

        // When & Then
        assertFailure(FailureType.INVALID_OUTPUT, false);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404})
    @DisplayName("generate_그 밖의 4xx 응답이면_재시도 불가한 BAD_REQUEST로 실패한다")
    void generate_otherHttp4xx_failsWithBadRequest(int status) {
        // Given
        server.expect(requestTo(RESPONSES_URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

        // When & Then
        assertFailure(FailureType.BAD_REQUEST, false);
    }

    @Test
    @DisplayName("generate_모델이 거절하면_REFUSED로 실패한다")
    void generate_refusal_failsWithRefused() {
        // Given
        String body = responseBody("completed", List.of(Map.of(
                "type", "message",
                "content", List.of(Map.of("type", "refusal", "refusal", "도와드릴 수 없습니다.")))));
        respondOk(body);

        // When & Then
        assertFailure(FailureType.REFUSED, false);
    }

    @Test
    @DisplayName("generate_status가 incomplete이면_INCOMPLETE로 실패한다")
    void generate_incompleteStatus_failsWithIncomplete() {
        // Given
        respondOk("""
                {"id":"resp_1","status":"incomplete",
                 "incomplete_details":{"reason":"max_output_tokens"},"output":[]}
                """);

        // When & Then
        assertFailure(FailureType.INCOMPLETE, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"failed", "in_progress", "queued", "cancelled"})
    @DisplayName("generate_status가 completed가 아니면_UPSTREAM_ERROR로 실패한다")
    void generate_notCompletedStatus_failsWithUpstreamError(String status) {
        // Given
        respondOk("{\"id\":\"resp_1\",\"status\":\"" + status + "\",\"output\":[]}");

        // When & Then
        assertFailure(FailureType.UPSTREAM_ERROR, true);
    }

    @Test
    @DisplayName("generate_status가 없으면_UPSTREAM_ERROR로 실패한다")
    void generate_missingStatus_failsWithUpstreamError() {
        // Given
        respondOk("{\"id\":\"resp_1\",\"output\":[]}");

        // When & Then
        assertFailure(FailureType.UPSTREAM_ERROR, true);
    }

    @Test
    @DisplayName("generate_본문을 받은 뒤 실패하면_예외가 사용량 지연시간 응답 ID를 담는다")
    void generate_incompleteWithUsage_exceptionCarriesUsageLatencyAndResponseId() {
        // Given
        respondOk("""
                {"id":"resp_9","status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},
                 "output":[],
                 "usage":{"input_tokens":1000,"input_tokens_details":{"cached_tokens":10},
                          "output_tokens":16000,"output_tokens_details":{"reasoning_tokens":15900}}}
                """);

        // When & Then
        assertThatThrownBy(() -> generator.generate(request(ImageDetail.AUTO, 500, null)))
                .isInstanceOfSatisfying(AiReviewGenerationException.class, exception -> {
                    assertThat(exception.getFailureType()).isEqualTo(FailureType.INCOMPLETE);
                    assertThat(exception.findAttempt()).contains(new Attempt(
                            new Usage(1000, 10, 16000, 15900),
                            Duration.ofMillis(1234),
                            "resp_9"));
                });
    }

    @Test
    @DisplayName("generate_출력 검증에 실패해도_예외가 사용량을 담는다")
    void generate_invalidOutput_exceptionCarriesUsage() {
        // Given
        respondOk(successBody("사진이 좋네요."));

        // When & Then
        assertThatThrownBy(() -> generator.generate(request(ImageDetail.AUTO, 500, null)))
                .isInstanceOfSatisfying(AiReviewGenerationException.class, exception -> {
                    assertThat(exception.getFailureType()).isEqualTo(FailureType.INVALID_OUTPUT);
                    assertThat(exception.findAttempt().orElseThrow().usage())
                            .isEqualTo(new Usage(1200, 300, 85, 40));
                    assertThat(exception.findAttempt().orElseThrow().responseId())
                            .isEqualTo("resp_123");
                });
    }

    @Test
    @DisplayName("generate_feedback이 301자면_INVALID_OUTPUT으로 실패한다")
    void generate_tooLongFeedback_failsWithInvalidOutput() {
        // Given
        respondOk(successBody("{\"compositionScore\":8,\"distinctivePerspective\":true,\"feedback\":\""
                + "가".repeat(301) + "\"}"));

        // When & Then
        assertFailure(FailureType.INVALID_OUTPUT, false);
    }

    @Test
    @DisplayName("generate_JSON이 아닌 출력이면_INVALID_OUTPUT으로 실패한다")
    void generate_nonJsonOutput_failsWithInvalidOutput() {
        // Given
        respondOk(successBody("사진이 좋네요."));

        // When & Then
        assertFailure(FailureType.INVALID_OUTPUT, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"compositionScore\":8,\"distinctivePerspective\":true}",
            "{\"distinctivePerspective\":true,\"feedback\":\"좋음\"}",
            "{\"compositionScore\":11,\"distinctivePerspective\":true,\"feedback\":\"좋음\"}",
            "{\"compositionScore\":8,\"distinctivePerspective\":true,\"feedback\":\" \"}"
    })
    @DisplayName("generate_필드가 없거나 범위를 벗어나면_INVALID_OUTPUT으로 실패한다")
    void generate_schemaViolation_failsWithInvalidOutput(String output) {
        // Given
        respondOk(successBody(output));

        // When & Then
        assertFailure(FailureType.INVALID_OUTPUT, false);
    }

    @Test
    @DisplayName("generate_429 응답이면_재시도 가능한 RATE_LIMITED로 실패하고 사용량은 없다")
    void generate_http429_failsWithRateLimited() {
        // Given
        server.expect(requestTo(RESPONSES_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        // When & Then
        assertThatThrownBy(() -> generator.generate(request(ImageDetail.AUTO, 500, null)))
                .isInstanceOfSatisfying(AiReviewGenerationException.class, exception -> {
                    assertThat(exception.getFailureType()).isEqualTo(FailureType.RATE_LIMITED);
                    assertThat(exception.getFailureType().isRetryable()).isTrue();
                    assertThat(exception.findAttempt()).isEmpty();
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503})
    @DisplayName("generate_5xx 응답이면_재시도 가능한 UPSTREAM_ERROR로 실패한다")
    void generate_http5xx_failsWithUpstreamError(int status) {
        // Given
        server.expect(requestTo(RESPONSES_URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

        // When & Then
        assertFailure(FailureType.UPSTREAM_ERROR, true);
    }

    @Test
    @DisplayName("generate_400 응답이면_재시도 불가한 BAD_REQUEST로 실패한다")
    void generate_http400_failsWithBadRequest() {
        // Given
        server.expect(requestTo(RESPONSES_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"secret prompt text\"}}"));

        // When & Then
        assertThatThrownBy(() -> generator.generate(request(ImageDetail.AUTO, 500, null)))
                .isInstanceOfSatisfying(AiReviewGenerationException.class, exception -> {
                    assertThat(exception.getFailureType()).isEqualTo(FailureType.BAD_REQUEST);
                    assertThat(exception.getFailureType().isRetryable()).isFalse();
                    assertThat(exception.getMessage()).doesNotContain("secret prompt text");
                });
    }

    @Test
    @DisplayName("generate_소켓 읽기 시간이 초과되면_재시도 가능한 TIMEOUT으로 실패한다")
    void generate_socketTimeout_failsWithTimeout() {
        // Given
        server.expect(requestTo(RESPONSES_URL)).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });

        // When & Then
        assertFailure(FailureType.TIMEOUT, true);
    }

    @Test
    @DisplayName("generate_JDK HTTP 클라이언트가 시간 초과를 던지면_TIMEOUT으로 실패한다")
    void generate_httpTimeout_failsWithTimeout() {
        // Given
        server.expect(requestTo(RESPONSES_URL)).andRespond(request -> {
            throw new HttpTimeoutException("request timed out");
        });

        // When & Then
        assertFailure(FailureType.TIMEOUT, true);
    }

    @Test
    @DisplayName("generate_연결 오류가 나면_재시도 가능한 UPSTREAM_ERROR로 실패한다")
    void generate_networkError_failsWithUpstreamError() {
        // Given
        server.expect(requestTo(RESPONSES_URL)).andRespond(request -> {
            throw new ConnectException("Connection refused");
        });

        // When & Then
        assertFailure(FailureType.UPSTREAM_ERROR, true);
    }

    @Test
    @DisplayName("generate_실패해도_예외 메시지에 API 키 프롬프트 이미지 주소를 담지 않는다")
    void generate_failure_doesNotLeakSensitiveValues() {
        // Given
        server.expect(requestTo(RESPONSES_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        // When & Then
        assertThatThrownBy(() -> generator.generate(request(ImageDetail.AUTO, 500, null)))
                .isInstanceOfSatisfying(AiReviewGenerationException.class, exception ->
                        assertThat(exception.getMessage())
                                .doesNotContain(API_KEY)
                                .doesNotContain("사진을 평가한다.")
                                .doesNotContain(IMAGE_URL));
    }

    private void respondOk(String body) {
        server.expect(requestTo(RESPONSES_URL))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void assertFailure(FailureType expected, boolean retryable) {
        assertThatThrownBy(() -> generator.generate(request(ImageDetail.AUTO, 500, null)))
                .isInstanceOfSatisfying(AiReviewGenerationException.class, exception -> {
                    assertThat(exception.getFailureType()).isEqualTo(expected);
                    assertThat(exception.getFailureType().isRetryable()).isEqualTo(retryable);
                });
    }

    private AiReviewGenerationRequest request(
            ImageDetail imageDetail,
            int maxOutputTokens,
            ReasoningEffort reasoningEffort
    ) {
        AiPromptDraft draft = new AiPromptDraft(
                AiPromptType.PHOTO_REVIEW,
                "사진을 평가한다.",
                "주제: {{topicTitle}}, 다시 {{topicTitle}}",
                "gpt-5-mini",
                new AiPromptParameters(imageDetail, maxOutputTokens, reasoningEffort));
        return new AiReviewGenerationRequest(draft, IMAGE_URL, "노을");
    }

    private String successBody(String outputText) {
        return responseBody("completed", List.of(Map.of(
                "type", "message",
                "content", List.of(Map.of("type", "output_text", "text", outputText)))));
    }

    private String responseBody(String status, List<Map<String, Object>> output) {
        return objectMapper.writeValueAsString(Map.of(
                "id", "resp_123",
                "status", status,
                "output", output,
                "usage", Map.of(
                        "input_tokens", 1200,
                        "input_tokens_details", Map.of("cached_tokens", 300),
                        "output_tokens", 85,
                        "output_tokens_details", Map.of("reasoning_tokens", 40))));
    }
}
