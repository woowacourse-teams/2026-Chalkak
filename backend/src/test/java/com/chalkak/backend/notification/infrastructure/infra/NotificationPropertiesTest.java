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
    @DisplayName("알림 발송을 켜면 안전한 Slack Webhook과 관리자 웹 Origin을 사용한다")
    void create_deliveryEnabledWithSafeUrls_succeeds() {
        // When
        NotificationProperties properties = createProperties(
                true,
                ADMIN_WEB_BASE_URL,
                SLACK_WEBHOOK_URL,
                Duration.ofSeconds(30)
        );

        // Then
        assertThat(properties.deliveryEnabled()).isTrue();
        assertThat(properties.adminWebBaseUrl()).isEqualTo(ADMIN_WEB_BASE_URL);
        assertThat(properties.slack().webhookUrl()).isEqualTo(SLACK_WEBHOOK_URL);
    }

    @Test
    @DisplayName("알림 발송을 끄면 배포 전 placeholder URL을 허용한다")
    void create_deliveryDisabledWithPlaceholderUrls_succeeds() {
        // When & Then
        NotificationProperties properties = createProperties(
                false,
                URI.create("https://REPLACE_WITH_ADMIN_WEB_DOMAIN"),
                URI.create("https://REPLACE_WITH_SLACK_WEBHOOK_URL"),
                Duration.ofSeconds(30)
        );

        assertThat(properties.deliveryEnabled()).isFalse();
    }

    @Test
    @DisplayName("알림 발송을 켜면 경로가 붙은 관리자 웹 주소를 거부한다")
    void create_deliveryEnabledWithAdminWebPath_throwsException() {
        assertThatThrownBy(() -> createProperties(
                true,
                URI.create("https://admin.example.com/posts"),
                SLACK_WEBHOOK_URL,
                Duration.ofSeconds(30)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("관리자 웹 기본 주소는 경로 없는 HTTPS Origin이어야 합니다.");
    }

    @Test
    @DisplayName("알림 발송을 켜면 Slack 이외 호스트의 Webhook을 거부한다")
    void create_deliveryEnabledWithNonSlackWebhook_throwsException() {
        assertThatThrownBy(() -> createProperties(
                true,
                ADMIN_WEB_BASE_URL,
                URI.create("https://attacker.example.com/services/TEST/TEST/TEST"),
                Duration.ofSeconds(30)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Slack Webhook은 hooks.slack.com의 HTTPS URL이어야 합니다.");
    }

    @Test
    @DisplayName("처리 lease가 Slack HTTP 최대 대기 시간보다 길지 않으면 거부한다")
    void create_processingLeaseNotLongerThanHttpTimeout_throwsException() {
        assertThatThrownBy(() -> createProperties(
                true,
                ADMIN_WEB_BASE_URL,
                SLACK_WEBHOOK_URL,
                Duration.ofSeconds(5)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("알림 처리 lease는 Slack HTTP 최대 대기 시간보다 길어야 합니다.");
    }

    private NotificationProperties createProperties(
            boolean deliveryEnabled,
            URI adminWebBaseUrl,
            URI slackWebhookUrl,
            Duration processingLease
    ) {
        return new NotificationProperties(
                deliveryEnabled,
                adminWebBaseUrl,
                Duration.ofSeconds(10),
                processingLease,
                20,
                5,
                Duration.ofSeconds(30),
                Duration.ofMinutes(10),
                new NotificationProperties.Slack(
                        slackWebhookUrl,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(3)
                )
        );
    }
}
