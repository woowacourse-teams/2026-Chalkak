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
import com.chalkak.backend.aireview.domain.PhotoReviewOutput;
import com.chalkak.backend.aireview.service.AiReviewGeneration.Attempt;
import com.chalkak.backend.aireview.service.AiReviewGeneration.Usage;
import com.chalkak.backend.aireview.service.AiReviewGeneration;
import com.chalkak.backend.aireview.service.AiReviewGenerationException.FailureType;
import com.chalkak.backend.aireview.service.AiReviewGenerationException;
import com.chalkak.backend.aireview.service.AiReviewGenerationRequest;
import java.io.IOException;
import java.io.InputStream;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
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
            "{\"composition\":8.72,\"theme_expression\":8,\"feedback\":\"구도가 좋습니다.\"}";

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
                Duration.ofSeconds(60),
                List.of("gpt-6.1-sol", "gpt-6-luna"));
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
                      "required": ["composition", "theme_expression", "feedback"],
                      "properties": {
                        "composition": {"type": "number", "minimum": 0, "maximum": 10},
                        "theme_expression": {"type": "number", "minimum": 0, "maximum": 10},
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
        assertThat(generation.output()).isEqualTo(new PhotoReviewOutput(8.72, 8.0, "구도가 좋습니다."));
        assertThat(generation.rawOutput()).isEqualTo(OK_OUTPUT);
        assertThat(generation.attempt().usage()).isEqualTo(new Usage(1200, 300, 85, 40));
        assertThat(generation.attempt().responseId()).isEqualTo("resp_123");
        assertThat(generation.attempt().latency()).isEqualTo(Duration.ofMillis(1234));
    }

    @Test
    @DisplayName("generate_기본 기준 7에서 주제 표현 점수가 6이면_독특한 시각이 아니다")
    void generate_defaultThreshold_themeSixIsNotDistinctive() {
        // Given
        respondOk(successBody(
                "{\"composition\":8,\"theme_expression\":6,\"feedback\":\"좋음\"}"));

        // When
        AiReviewGeneration generation = generator.generate(request(ImageDetail.AUTO, 500, null));

        // Then
        assertThat(generation.result().distinctivePerspective()).isFalse();
    }

    @Test
    @DisplayName("generate_프롬프트에 기준 6을 지정하면_주제 표현 점수 6도 독특한 시각이다")
    void generate_customThreshold_isAppliedToJudgement() {
        // Given
        respondOk(successBody(
                "{\"composition\":8,\"theme_expression\":6,\"feedback\":\"좋음\"}"));

        // When
        AiReviewGeneration generation =
                generator.generate(request(ImageDetail.AUTO, 500, null, 6.0));

        // Then
        assertThat(generation.result().distinctivePerspective()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"7.5, true", "7.4, false", "8.0, true"})
    @DisplayName("generate_소수 기준 7.5와 소수 주제 표현 점수를_그대로 비교한다")
    void generate_decimalThreshold_comparesDecimalScores(String theme, boolean expected) {
        // Given
        respondOk(successBody(
                "{\"composition\":8,\"theme_expression\":" + theme + ",\"feedback\":\"좋음\"}"));

        // When
        AiReviewGeneration generation =
                generator.generate(request(ImageDetail.AUTO, 500, null, 7.5));

        // Then
        assertThat(generation.result().distinctivePerspective()).isEqualTo(expected);
        assertThat(generation.output().themeExpressionScore()).isEqualTo(Double.parseDouble(theme));
    }

    @Test
    @DisplayName("supportsModel_허용 목록의 모델이면_true이고 그 밖의 모델이면_false이다")
    void supportsModel_allowedModels_matchesExactly() {
        // When & Then
        assertThat(generator.supportsModel("gpt-6.1-sol")).isTrue();
        assertThat(generator.supportsModel("gpt-6-luna")).isTrue();
        assertThat(generator.supportsModel("gpt-6.1-SOL")).isFalse();
        assertThat(generator.supportsModel("gpt-unknown")).isFalse();
        assertThat(generator.supportsModel(null)).isFalse();
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

    @ParameterizedTest
    @CsvSource({
            "invalid_prompt, BAD_REQUEST, false",
            "data_residency_mismatch, BAD_REQUEST, false",
            "invalid_image, BAD_REQUEST, false",
            "invalid_image_format, BAD_REQUEST, false",
            "invalid_base64_image, BAD_REQUEST, false",
            "invalid_image_url, BAD_REQUEST, false",
            "image_too_large, BAD_REQUEST, false",
            "image_too_small, BAD_REQUEST, false",
            "image_parse_error, BAD_REQUEST, false",
            "invalid_image_mode, BAD_REQUEST, false",
            "image_file_too_large, BAD_REQUEST, false",
            "unsupported_image_media_type, BAD_REQUEST, false",
            "empty_image_file, BAD_REQUEST, false",
            "image_file_not_found, BAD_REQUEST, false",
            "image_content_policy_violation, REFUSED, false",
            "bio_policy, REFUSED, false",
            "misalignment_policy_violation, REFUSED, false",
            "rate_limit_exceeded, RATE_LIMITED, true",
            "server_error, UPSTREAM_ERROR, true",
            "vector_store_timeout, UPSTREAM_ERROR, true",
            "failed_to_download_image, UPSTREAM_ERROR, true",
            "brand_new_code, UPSTREAM_ERROR, true"
    })
    @DisplayName("generate_status가 failed이면_error.code로 실패 유형을 가르고 시도 정보를 담는다")
    void generate_failedStatus_classifiesByErrorCode(
            String code, FailureType expected, boolean retryable
    ) {
        // Given
        respondOk("{\"id\":\"resp_f\",\"status\":\"failed\",\"output\":[],"
                + "\"error\":{\"code\":\"" + code + "\",\"message\":\"secret detail\"}}");

        // When & Then
        assertThatThrownBy(() -> generator.generate(request(ImageDetail.AUTO, 500, null)))
                .isInstanceOfSatisfying(AiReviewGenerationException.class, exception -> {
                    assertThat(exception.getFailureType()).isEqualTo(expected);
                    assertThat(exception.getFailureType().isRetryable()).isEqualTo(retryable);
                    assertThat(exception.getMessage()).doesNotContain("secret detail");
                    assertThat(exception.findAttempt().orElseThrow().responseId())
                            .isEqualTo("resp_f");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"error\":null",
            "\"error\":{}",
            "\"error\":{\"code\":null,\"message\":\"x\"}"
    })
    @DisplayName("generate_status가 failed인데 error.code가 없으면_재시도 가능한 UPSTREAM_ERROR로 실패한다")
    void generate_failedWithoutErrorCode_failsWithUpstreamError(String error) {
        // Given
        respondOk("{\"id\":\"resp_f\",\"status\":\"failed\",\"output\":[]," + error + "}");

        // When & Then
        assertThatThrownBy(() -> generator.generate(request(ImageDetail.AUTO, 500, null)))
                .isInstanceOfSatisfying(AiReviewGenerationException.class, exception -> {
                    assertThat(exception.getFailureType()).isEqualTo(FailureType.UPSTREAM_ERROR);
                    assertThat(exception.getFailureType().isRetryable()).isTrue();
                    assertThat(exception.findAttempt().orElseThrow().responseId())
                            .isEqualTo("resp_f");
                });
    }

    @Test
    @DisplayName("generate_본문이 JSON null이면_INVALID_OUTPUT으로 실패하고 시도 정보는 없다")
    void generate_nullBody_failsWithInvalidOutput() {
        // Given
        respondOk("null");

        // When & Then
        assertThatThrownBy(() -> generator.generate(request(ImageDetail.AUTO, 500, null)))
                .isInstanceOfSatisfying(AiReviewGenerationException.class, exception -> {
                    assertThat(exception.getFailureType()).isEqualTo(FailureType.INVALID_OUTPUT);
                    assertThat(exception.findAttempt()).isEmpty();
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[null]",
            "[{\"type\":\"message\",\"content\":[null]}]"
    })
    @DisplayName("generate_output이나 content에 null 요소가 있으면_시도 정보를 담아 INVALID_OUTPUT으로 실패한다")
    void generate_nullElementInOutput_failsWithInvalidOutputAndAttempt(String output) {
        // Given
        respondOk("{\"id\":\"resp_x\",\"status\":\"completed\",\"output\":" + output + "}");

        // When & Then
        assertThatThrownBy(() -> generator.generate(request(ImageDetail.AUTO, 500, null)))
                .isInstanceOfSatisfying(AiReviewGenerationException.class, exception -> {
                    assertThat(exception.getFailureType()).isEqualTo(FailureType.INVALID_OUTPUT);
                    assertThat(exception.findAttempt().orElseThrow().responseId())
                            .isEqualTo("resp_x");
                });
    }

    @Test
    @DisplayName("generate_message의 content가 null이면_INVALID_OUTPUT으로 실패한다")
    void generate_nullContent_failsWithInvalidOutput() {
        // Given
        respondOk("{\"id\":\"resp_1\",\"status\":\"completed\","
                + "\"output\":[{\"type\":\"message\",\"content\":null}]}");

        // When & Then
        assertFailure(FailureType.INVALID_OUTPUT, false);
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
        respondOk(successBody("{\"composition\":8,\"theme_expression\":8,\"feedback\":\""
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
            "{\"composition\":8,\"theme_expression\":8}",
            "{\"theme_expression\":8,\"feedback\":\"좋음\"}",
            "{\"composition\":8,\"feedback\":\"좋음\"}",
            "{\"composition\":11,\"theme_expression\":8,\"feedback\":\"좋음\"}",
            "{\"composition\":8,\"theme_expression\":11,\"feedback\":\"좋음\"}",
            "{\"composition\":8,\"theme_expression\":99999999999,\"feedback\":\"좋음\"}",
            "{\"composition\":8,\"theme_expression\":null,\"feedback\":\"좋음\"}",
            "{\"composition\":null,\"theme_expression\":8,\"feedback\":\"좋음\"}",
            "{\"composition\":8,\"theme_expression\":\"7\",\"feedback\":\"좋음\"}",
            "{\"composition\":\"8\",\"theme_expression\":7,\"feedback\":\"좋음\"}",
            "{\"composition\":8,\"theme_expression\":7,\"feedback\":\" \"}",
            "{\"composition\":8,\"theme_expression\":7,\"feedback\":null}"
    })
    @DisplayName("generate_필드가 없거나 범위를 벗어나면_INVALID_OUTPUT으로 실패한다")
    void generate_schemaViolation_failsWithInvalidOutput(String output) {
        // Given
        respondOk(successBody(output));

        // When & Then
        assertFailure(FailureType.INVALID_OUTPUT, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"error\":{\"message\":\"Rate limit reached for requests\","
                    + "\"type\":\"requests\",\"code\":\"rate_limit_exceeded\"}}",
            "{\"error\":{\"message\":\"Slow down\","
                    + "\"type\":\"rate_limit_error\",\"code\":\"slow_down\"}}",
            "{\"error\":{\"message\":\"unknown\",\"type\":\"other\",\"code\":\"new_code\"}}",
            "{\"error\":{\"message\":\"no code\"}}",
            "{\"error\":null}",
            "{\"error\":\"quota\"}",
            "[]",
            "{}",
            "",
            "not json"
    })
    @DisplayName("generate_일시적 한도이거나 원인을 알 수 없는 429 응답이면_재시도 가능한 RATE_LIMITED로 실패하고 사용량은 없다")
    void generate_transientHttp429_failsWithRateLimited(String errorBody) {
        // Given
        respondError(HttpStatus.TOO_MANY_REQUESTS, errorBody);

        // When & Then
        assertThatThrownBy(() -> generator.generate(request(ImageDetail.AUTO, 500, null)))
                .isInstanceOfSatisfying(AiReviewGenerationException.class, exception -> {
                    assertThat(exception.getFailureType()).isEqualTo(FailureType.RATE_LIMITED);
                    assertThat(exception.getFailureType().isRetryable()).isTrue();
                    assertThat(exception.findAttempt()).isEmpty();
                });
    }

    @Test
    @DisplayName("generate_본문 없는 429 응답이면_재시도 가능한 RATE_LIMITED로 실패한다")
    void generate_http429WithoutBody_failsWithRateLimited() {
        // Given
        server.expect(requestTo(RESPONSES_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        // When & Then
        assertFailure(FailureType.RATE_LIMITED, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "insufficient_quota",
            "credit_balance_exhausted",
            "organization_spend_limit_exceeded",
            "project_spend_limit_exceeded",
            "organization_usage_limit_exceeded"
    })
    @DisplayName("generate_결제·한도 소진 코드의 429 응답이면_재시도 불가한 QUOTA_EXCEEDED로 실패한다")
    void generate_billingHttp429_failsWithQuotaExceeded(String code) {
        // Given
        respondError(HttpStatus.TOO_MANY_REQUESTS,
                "{\"error\":{\"message\":\"secret billing detail\",\"type\":\"billing\","
                        + "\"code\":\"" + code + "\"}}");

        // When & Then
        assertThatThrownBy(() -> generator.generate(request(ImageDetail.AUTO, 500, null)))
                .isInstanceOfSatisfying(AiReviewGenerationException.class, exception -> {
                    assertThat(exception.getFailureType()).isEqualTo(FailureType.QUOTA_EXCEEDED);
                    assertThat(exception.getFailureType().isRetryable()).isFalse();
                    assertThat(exception.getMessage()).doesNotContain("secret billing detail");
                    assertThat(exception.findAttempt()).isEmpty();
                });
    }

    @Test
    @DisplayName("generate_type만 insufficient_quota인 429 응답이면_재시도 불가한 QUOTA_EXCEEDED로 실패한다")
    void generate_http429WithQuotaType_failsWithQuotaExceeded() {
        // Given
        respondError(HttpStatus.TOO_MANY_REQUESTS,
                "{\"error\":{\"message\":\"quota\",\"type\":\"insufficient_quota\",\"code\":null}}");

        // When & Then
        assertFailure(FailureType.QUOTA_EXCEEDED, false);
    }

    @Test
    @DisplayName("generate_결제 코드가 429가 아닌 응답에 있으면_상태 코드 기준으로 분류한다")
    void generate_billingCodeOnNon429_classifiedByStatus() {
        // Given
        respondError(HttpStatus.BAD_REQUEST,
                "{\"error\":{\"type\":\"insufficient_quota\",\"code\":\"insufficient_quota\"}}");

        // When & Then
        assertFailure(FailureType.BAD_REQUEST, false);
    }

    @Test
    @DisplayName("generate_408 응답이면_재시도 가능한 TIMEOUT으로 실패한다")
    void generate_http408_failsWithTimeout() {
        // Given
        server.expect(requestTo(RESPONSES_URL)).andRespond(withStatus(HttpStatus.REQUEST_TIMEOUT));

        // When & Then
        assertFailure(FailureType.TIMEOUT, true);
    }

    @Test
    @DisplayName("generate_409 응답이면_재시도 가능한 UPSTREAM_ERROR로 실패한다")
    void generate_http409_failsWithUpstreamError() {
        // Given
        server.expect(requestTo(RESPONSES_URL)).andRespond(withStatus(HttpStatus.CONFLICT));

        // When & Then
        assertFailure(FailureType.UPSTREAM_ERROR, true);
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
    @DisplayName("generate_본문을 읽다 실패했고 경과 시간이 제한 미만이면_재시도 가능한 UPSTREAM_ERROR로 실패한다")
    void generate_bodyReadFailureBeforeDeadline_failsWithUpstreamError() {
        // Given
        respondWithBrokenBody();

        // When & Then
        assertFailure(FailureType.UPSTREAM_ERROR, true);
    }

    @Test
    @DisplayName("generate_본문을 읽다 실패했고 경과 시간이 제한 이상이면_재시도 가능한 TIMEOUT으로 실패한다")
    void generate_bodyReadFailureAfterDeadline_failsWithTimeout() {
        // Given
        Instant start = Instant.parse("2026-10-19T00:00:00Z");
        when(clock.instant()).thenReturn(start, start.plusSeconds(60));
        respondWithBrokenBody();

        // When & Then
        assertFailure(FailureType.TIMEOUT, true);
    }

    @Test
    @DisplayName("generate_연결 오류가 제한 시간 이상 지난 뒤 나면_TIMEOUT으로 실패한다")
    void generate_networkErrorAfterDeadline_failsWithTimeout() {
        // Given
        Instant start = Instant.parse("2026-10-19T00:00:00Z");
        when(clock.instant()).thenReturn(start, start.plusSeconds(61));
        server.expect(requestTo(RESPONSES_URL)).andRespond(request -> {
            throw new ConnectException("Connection reset");
        });

        // When & Then
        assertFailure(FailureType.TIMEOUT, true);
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

    private void respondWithBrokenBody() {
        server.expect(requestTo(RESPONSES_URL)).andRespond(request -> {
            InputStream broken = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("closed");
                }
            };
            MockClientHttpResponse response = new MockClientHttpResponse(broken, HttpStatus.OK);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return response;
        });
    }

    private void respondError(HttpStatus status, String body) {
        server.expect(requestTo(RESPONSES_URL))
                .andRespond(withStatus(status)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body));
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
        return request(imageDetail, maxOutputTokens, reasoningEffort, null);
    }

    private AiReviewGenerationRequest request(
            ImageDetail imageDetail,
            int maxOutputTokens,
            ReasoningEffort reasoningEffort,
            Double threshold
    ) {
        AiPromptDraft draft = new AiPromptDraft(
                AiPromptType.PHOTO_REVIEW,
                "사진을 평가한다.",
                "주제: {{topicTitle}}, 다시 {{topicTitle}}",
                "gpt-5-mini",
                new AiPromptParameters(imageDetail, maxOutputTokens, reasoningEffort, threshold));
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
