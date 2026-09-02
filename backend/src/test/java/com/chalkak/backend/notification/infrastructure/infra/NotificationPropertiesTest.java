package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationPropertiesTest {

    private static final URI ADMIN_WEB_BASE_URL =
            URI.create("https://admin.example.com");
    private static final URI SLACK_WEBHOOK_URL =
            URI.create("https://hooks.slack.com/services/TEST/TEST/TEST");

    @Test
    @DisplayName("안전한 Slack Webhook과 관리자 웹 Origin을 사용한다")
    void create_safeUrls_succeeds() {
        // When
        NotificationProperties properties = createProperties(
                ADMIN_WEB_BASE_URL,
                SLACK_WEBHOOK_URL
        );

        // Then
        assertThat(properties.adminWebBaseUrl()).isEqualTo(ADMIN_WEB_BASE_URL);
        assertThat(properties.slack().webhookUrl()).isEqualTo(SLACK_WEBHOOK_URL);
    }

    @Test
    @DisplayName("경로가 붙은 관리자 웹 주소를 거부한다")
    void create_adminWebPath_throwsException() {
        assertThatThrownBy(() -> createProperties(
                URI.create("https://admin.example.com/posts"),
                SLACK_WEBHOOK_URL
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("관리자 웹 기본 주소는 경로 없는 HTTPS Origin이어야 합니다.");
    }

    @Test
    @DisplayName("Slack 이외 호스트의 Webhook을 거부한다")
    void create_nonSlackWebhook_throwsException() {
        assertThatThrownBy(() -> createProperties(
                ADMIN_WEB_BASE_URL,
                URI.create("https://attacker.example.com/services/TEST/TEST/TEST")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Slack Webhook은 hooks.slack.com의 HTTPS URL이어야 합니다.");
    }

    @Test
    @DisplayName("마지막 슬래시가 붙은 Slack Webhook을 거부한다")
    void create_slackWebhookWithTrailingSlash_throwsException() {
        assertThatThrownBy(() -> createProperties(
                ADMIN_WEB_BASE_URL,
                URI.create("https://hooks.slack.com/services/TEST/TEST/TEST/")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Slack Webhook은 hooks.slack.com의 HTTPS URL이어야 합니다.");
    }

    private NotificationProperties createProperties(
            URI adminWebBaseUrl,
            URI slackWebhookUrl
    ) {
        return new NotificationProperties(
                adminWebBaseUrl,
                new NotificationProperties.Slack(
                        slackWebhookUrl,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(3)
                )
        );
    }
}
