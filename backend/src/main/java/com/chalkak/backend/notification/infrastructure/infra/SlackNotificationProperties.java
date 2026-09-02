package com.chalkak.backend.notification.infrastructure.infra;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("chalkak.admin.notification.slack")
public record SlackNotificationProperties(
        URI webhookUrl,
        Duration connectTimeout,
        Duration readTimeout
) {

    private static final String HTTPS_SCHEME = "https";
    private static final String SLACK_WEBHOOK_HOST = "hooks.slack.com";
    private static final String SLACK_WEBHOOK_PATH_PREFIX = "/services/";

    public SlackNotificationProperties {
        Objects.requireNonNull(webhookUrl, "Slack Webhook URL이 필요합니다.");
        validatePositive(connectTimeout, "Slack 연결 제한 시간은 0보다 커야 합니다.");
        validatePositive(readTimeout, "Slack 응답 제한 시간은 0보다 커야 합니다.");

        if (!isSlackWebhook(webhookUrl)) {
            throw new IllegalArgumentException(
                    "Slack Webhook은 hooks.slack.com의 HTTPS URL이어야 합니다."
            );
        }
    }

    private static void validatePositive(Duration duration, String message) {
        Objects.requireNonNull(duration, message);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean isSlackWebhook(URI uri) {
        if (!HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        if (!SLACK_WEBHOOK_HOST.equalsIgnoreCase(uri.getHost())) {
            return false;
        }
        if (uri.getPort() != -1
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            return false;
        }
        String path = uri.getRawPath();
        if (path == null || !path.startsWith(SLACK_WEBHOOK_PATH_PREFIX)) {
            return false;
        }
        String[] pathSegments = path
                .substring(SLACK_WEBHOOK_PATH_PREFIX.length())
                .split("/", -1);
        return pathSegments.length == 3
                && !pathSegments[0].isBlank()
                && !pathSegments[1].isBlank()
                && !pathSegments[2].isBlank();
    }
}
