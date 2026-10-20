package com.chalkak.backend.aireview.infrastructure.infra.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.aireview.domain.AiPromptDraft;
import com.chalkak.backend.aireview.domain.AiPromptParameters;
import com.chalkak.backend.aireview.domain.AiPromptType;
import com.chalkak.backend.aireview.service.AiReviewGenerationException.FailureType;
import com.chalkak.backend.aireview.service.AiReviewGenerationException;
import com.chalkak.backend.aireview.service.AiReviewGenerationRequest;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 운영과 같은 HttpClient·RestClient 구성으로 실제 읽기 제한 시간이 TIMEOUT으로 분류되는지 확인한다.
 */
class OpenAiRealTimeoutTest {

    private static final long RESPONSE_DELAY_MILLIS = 1500;

    private HttpServer stub;

    @BeforeEach
    void setUp() throws Exception {
        stub = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        stub.createContext("/v1/responses", exchange -> {
            try {
                Thread.sleep(RESPONSE_DELAY_MILLIS);
                exchange.sendResponseHeaders(200, -1);
            } catch (Exception ignored) {
                // 클라이언트가 먼저 끊으면 응답할 필요가 없다.
            } finally {
                exchange.close();
            }
        });
        stub.createContext("/partial/responses", exchange -> {
            try {
                // 헤더와 본문 일부만 보낸 채 멈춰, 헤더 수신 뒤 본문을 읽다 제한 시간이 지나는 상황을 만든다.
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, 10_000);
                exchange.getResponseBody()
                        .write("{\"id\":\"resp_1\",".getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                Thread.sleep(RESPONSE_DELAY_MILLIS);
            } catch (Exception ignored) {
                // 클라이언트가 먼저 끊으면 응답할 필요가 없다.
            } finally {
                exchange.close();
            }
        });
        stub.start();
    }

    @AfterEach
    void tearDown() {
        stub.stop(0);
    }

    @Test
    @DisplayName("generate_응답이 읽기 제한 시간보다 늦으면_재시도 가능한 TIMEOUT으로 실패한다")
    void generate_slowResponse_failsWithTimeout() {
        // When & Then
        assertTimeout("/v1");
    }

    @Test
    @DisplayName("generate_헤더를 받은 뒤 본문이 제한 시간 안에 끝나지 않으면_재시도 가능한 TIMEOUT으로 실패한다")
    void generate_slowBody_failsWithTimeout() {
        // When & Then
        assertTimeout("/partial");
    }

    private void assertTimeout(String basePath) {
        // Given
        OpenAiProperties properties = new OpenAiProperties(
                "test-key",
                URI.create("http://127.0.0.1:" + stub.getAddress().getPort() + basePath),
                Duration.ofSeconds(2),
                Duration.ofMillis(200),
                List.of("gpt-6.1-sol")
        );
        OpenAiConfiguration configuration = new OpenAiConfiguration();
        OpenAiResponsesReviewGenerator generator = new OpenAiResponsesReviewGenerator(
                configuration.openAiRestClient(configuration.openAiHttpClient(properties),
                        properties),
                JsonMapper.builder().build(),
                Clock.systemUTC(),
                properties
        );
        AiPromptDraft draft = new AiPromptDraft(
                AiPromptType.PHOTO_REVIEW,
                "사진을 평가한다.",
                "주제: {{topicTitle}}",
                "gpt-5-mini",
                new AiPromptParameters(
                        null,
                        500,
                        null,
                        null
                )
        );

        // When & Then
        assertThatThrownBy(() -> generator.generate(
                new AiReviewGenerationRequest(
                        draft,
                        "https://cdn.test/a.jpg",
                        "노을"
                )))
                .isInstanceOfSatisfying(AiReviewGenerationException.class, exception -> {
                    assertThat(exception.getFailureType()).isEqualTo(FailureType.TIMEOUT);
                    assertThat(exception.getFailureType().isRetryable()).isTrue();
                });
    }
}
