package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.chalkak.backend.notification.domain.NotificationChannel;
import com.chalkak.backend.notification.service.NotificationMessage;
import com.chalkak.backend.notification.service.NotificationSendOutcome;
import com.chalkak.backend.notification.service.NotificationSendResult;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
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
    @DisplayName("Slack 채널을 지원한다")
    void supportedChannel_always_returnsSlack() {
        // When
        NotificationChannel channel = sender.supportedChannel();

        // Then
        assertThat(channel).isEqualTo(NotificationChannel.SLACK);
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
        NotificationSendResult result = sender.send(MESSAGE);

        // Then
        assertThat(result).isEqualTo(NotificationSendResult.sent());
    }

    @Test
    @DisplayName("429 응답의 Retry-After 초를 재시도 간격으로 반환한다")
    void send_rateLimitedResponse_returnsRetryableWithRetryAfter() {
        // Given
        server.expect(requestTo(WEBHOOK_URI))
                .andRespond(withStatus(TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "73")
                        .body("SECRET_RATE_LIMIT_RESPONSE"));

        // When
        NotificationSendResult result = sender.send(MESSAGE);

        // Then
        assertThat(result.outcome()).isEqualTo(NotificationSendOutcome.RETRYABLE_FAILURE);
        assertThat(result.retryAfter()).isEqualTo(Duration.ofSeconds(73));
        assertThat(result.failureCode()).isEqualTo("SLACK_RATE_LIMITED");
    }

    @Test
    @DisplayName("429를 제외한 4xx 응답은 재시도하지 않는 실패로 반환한다")
    void send_clientError_returnsPermanentFailure() {
        // Given
        server.expect(requestTo(WEBHOOK_URI))
                .andRespond(withStatus(BAD_REQUEST).body("SECRET_CLIENT_ERROR_RESPONSE"));

        // When
        NotificationSendResult result = sender.send(MESSAGE);

        // Then
        assertThat(result).isEqualTo(
                NotificationSendResult.permanentFailure("SLACK_CLIENT_ERROR")
        );
    }

    @Test
    @DisplayName("5xx 응답은 재시도 가능한 실패로 반환한다")
    void send_serverError_returnsRetryableFailure() {
        // Given
        server.expect(requestTo(WEBHOOK_URI))
                .andRespond(withStatus(INTERNAL_SERVER_ERROR)
                        .body("SECRET_SERVER_ERROR_RESPONSE"));

        // When
        NotificationSendResult result = sender.send(MESSAGE);

        // Then
        assertThat(result).isEqualTo(
                NotificationSendResult.retryable(null, "SLACK_SERVER_ERROR")
        );
    }

    @Test
    @DisplayName("응답 시간 초과는 재시도 가능한 시간 초과 실패로 반환한다")
    void send_timeout_returnsRetryableTimeoutFailure() {
        // Given
        server.expect(requestTo(WEBHOOK_URI))
                .andRespond(request -> {
                    throw new SocketTimeoutException("SECRET_TIMEOUT_DETAIL");
                });

        // When
        NotificationSendResult result = sender.send(MESSAGE);

        // Then
        assertThat(result).isEqualTo(
                NotificationSendResult.retryable(null, "SLACK_TIMEOUT")
        );
    }

    @Test
    @DisplayName("그 밖의 통신 실패는 재시도 가능한 네트워크 실패로 반환한다")
    void send_networkError_returnsRetryableNetworkFailure() {
        // Given
        server.expect(requestTo(WEBHOOK_URI))
                .andRespond(request -> {
                    throw new IOException("SECRET_NETWORK_DETAIL");
                });

        // When
        NotificationSendResult result = sender.send(MESSAGE);

        // Then
        assertThat(result).isEqualTo(
                NotificationSendResult.retryable(null, "SLACK_NETWORK_ERROR")
        );
    }

    @Test
    @DisplayName("Slack 응답 본문과 예외 원문과 웹훅 주소를 결과에 노출하지 않는다")
    void send_failure_doesNotExposeProviderSecrets() {
        // Given
        String responseSecret = "SECRET_PROVIDER_RESPONSE_BODY";
        server.expect(requestTo(WEBHOOK_URI))
                .andRespond(withStatus(INTERNAL_SERVER_ERROR).body(responseSecret));

        // When
        NotificationSendResult result = sender.send(MESSAGE);

        // Then
        assertThat(result.toString())
                .doesNotContain(responseSecret)
                .doesNotContain(WEBHOOK_URI.toString())
                .doesNotContain("SECRET_WEBHOOK_PATH");
    }
}
