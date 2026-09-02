package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlackNotificationPropertiesTest {

    private static final URI SLACK_WEBHOOK_URL =
            URI.create("https://hooks.slack.com/services/TEST/TEST/TEST");

    @Test
    @DisplayName("공식 Slack Webhook과 양수 제한 시간을 사용한다")
    void create_safeWebhookAndTimeouts_succeeds() {
        // When
        SlackNotificationProperties properties = createProperties(
                SLACK_WEBHOOK_URL,
                Duration.ofSeconds(2),
                Duration.ofSeconds(3)
        );

        // Then
        assertThat(properties.webhookUrl()).isEqualTo(SLACK_WEBHOOK_URL);
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    @DisplayName("Slack 이외 호스트의 Webhook을 거부한다")
    void create_nonSlackWebhook_throwsException() {
        assertThatThrownBy(() -> createProperties(
                URI.create("https://attacker.example.com/services/TEST/TEST/TEST"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Slack Webhook은 hooks.slack.com의 HTTPS URL이어야 합니다.");
    }

    @Test
    @DisplayName("마지막 슬래시가 붙은 Slack Webhook을 거부한다")
    void create_slackWebhookWithTrailingSlash_throwsException() {
        assertThatThrownBy(() -> createProperties(
                URI.create("https://hooks.slack.com/services/TEST/TEST/TEST/"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Slack Webhook은 hooks.slack.com의 HTTPS URL이어야 합니다.");
    }

    @Test
    @DisplayName("0 이하인 Slack 연결 제한 시간을 거부한다")
    void create_nonPositiveConnectTimeout_throwsException() {
        assertThatThrownBy(() -> createProperties(
                SLACK_WEBHOOK_URL,
                Duration.ZERO,
                Duration.ofSeconds(3)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Slack 연결 제한 시간은 0보다 커야 합니다.");
    }

    @Test
    @DisplayName("0 이하인 Slack 응답 제한 시간을 거부한다")
    void create_nonPositiveReadTimeout_throwsException() {
        assertThatThrownBy(() -> createProperties(
                SLACK_WEBHOOK_URL,
                Duration.ofSeconds(2),
                Duration.ofSeconds(-1)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Slack 응답 제한 시간은 0보다 커야 합니다.");
    }

    private SlackNotificationProperties createProperties(
            URI slackWebhookUrl,
            Duration connectTimeout,
            Duration readTimeout
    ) {
        return new SlackNotificationProperties(
                slackWebhookUrl,
                connectTimeout,
                readTimeout
        );
    }
}
