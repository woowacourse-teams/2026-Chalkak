package com.chalkak.backend.auth.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.support.IntegrationTestSupport;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 필터가 인증보다 먼저 도는지 확인한다. 서명 헤더가 전혀 없는 요청이 401이 아니라 413을 받아야, 본문이
 * 인증 이전에 메모리로 올라오지 않았다는 뜻이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CallbackBodySizeLimitFilterTest extends IntegrationTestSupport {

    private static final UUID UPLOAD_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");

    @LocalServerPort
    private int port;

    @MockitoBean
    private ProcessingCallbackAuthenticator authenticator;

    private HttpResponse<String> post(String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port
                        + "/internal/v1/post-image-processing/" + UPLOAD_ID + "/complete"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        return HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("상한을 넘는 본문은 인증까지 가지 않고 413을 받는다")
    void oversizedBody_rejectedBeforeAuthentication() throws Exception {
        // Given
        String body = "{\"capturedAt\":\"" + "A".repeat(70_000) + "\"}";

        // When
        HttpResponse<String> response = post(body);

        // Then
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("이미지 처리 콜백 본문이 너무 큽니다.");
    }

    @Test
    @DisplayName("상한 안의 본문은 필터를 통과해 인증 단계까지 간다")
    void normalBody_reachesAuthentication() throws Exception {
        // Given
        String body = "{\"width\":4032,\"height\":3024}";

        // When
        HttpResponse<String> response = post(body);

        // Then
        assertThat(response.statusCode()).isNotEqualTo(413);
    }
}
