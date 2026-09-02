package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.chalkak.backend.notification.service.NotificationMessage;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SlackIncomingWebhookNotificationSenderTest {

    private static final URI WEBHOOK_URI = URI.create(
            "https://hooks.slack.test/services/SECRET_WEBHOOK_PATH"
    );
    private static final NotificationMessage MESSAGE = new NotificationMessage(
            "새 게시물 검수 요청",
            "새 게시물이 승인 대기 상태가 되었습니다.",
            "관리자 웹에서 검수하기",
            URI.create("https://admin.example.com/posts/0199a000-0000-7000-8000-000000000001"),
            Instant.parse("2026-09-02T00:00:00Z")
    );

    private MockRestServiceServer server;
    private SlackIncomingWebhookNotificationSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new SlackIncomingWebhookNotificationSender(builder.build(), WEBHOOK_URI);
    }

    @AfterEach
    void tearDown() {
        server.verify();
    }

    @Test
    @DisplayName("Block Kit JSON에 검수 내용과 정확한 링크를 담아 POST하고 2xx를 성공으로 반환한다")
    void send_successfulResponse_postsBlockKitAndReturnsSent() {
        // Given
        server.expect(requestTo(WEBHOOK_URI))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.text").value(MESSAGE.title()))
                .andExpect(jsonPath("$.blocks[0].type").value("header"))
                .andExpect(jsonPath("$.blocks[0].text.type").value("plain_text"))
                .andExpect(jsonPath("$.blocks[0].text.text").value(MESSAGE.title()))
                .andExpect(jsonPath("$.blocks[1].text.text").value(MESSAGE.body()))
                .andExpect(jsonPath("$.blocks[2].elements[0].text")
                        .value("발생 시각: 2026-09-02T00:00:00Z"))
                .andExpect(jsonPath("$.blocks[3].text.text").value(
                        "<https://admin.example.com/posts/0199a000-0000-7000-8000-000000000001"
                                + "|관리자 웹에서 검수하기>"
                ))
                .andRespond(withSuccess("ok", org.springframework.http.MediaType.TEXT_PLAIN));

        // When
        boolean result = sender.send(MESSAGE);

        // Then
        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 429, 500})
    @DisplayName("2xx가 아닌 Slack 응답은 발송 실패로 반환한다")
    void send_unsuccessfulResponse_returnsFalse(int statusCode) {
        // Given
        server.expect(requestTo(WEBHOOK_URI))
                .andRespond(withStatus(HttpStatusCode.valueOf(statusCode))
                        .body("SECRET_PROVIDER_RESPONSE"));

        // When
        boolean result = sender.send(MESSAGE);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("응답 시간 초과는 발송 실패로 반환한다")
    void send_timeout_returnsFalse() {
        // Given
        server.expect(requestTo(WEBHOOK_URI))
                .andRespond(request -> {
                    throw new SocketTimeoutException("SECRET_TIMEOUT_DETAIL");
                });

        // When
        boolean result = sender.send(MESSAGE);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("그 밖의 통신 실패도 발송 실패로 반환한다")
    void send_networkError_returnsFalse() {
        // Given
        server.expect(requestTo(WEBHOOK_URI))
                .andRespond(request -> {
                    throw new IOException("SECRET_NETWORK_DETAIL");
                });

        // When
        boolean result = sender.send(MESSAGE);

        // Then
        assertThat(result).isFalse();
    }
}
