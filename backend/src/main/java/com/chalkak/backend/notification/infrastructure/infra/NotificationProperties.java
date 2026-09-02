package com.chalkak.backend.notification.infrastructure.infra;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("chalkak.admin.notification")
public record NotificationProperties(
        boolean deliveryEnabled,
        URI adminWebBaseUrl,
        Duration pollInterval,
        Duration processingLease,
        int batchSize,
        int maxAttempts,
        Duration initialRetryDelay,
        Duration maxRetryDelay,
        Slack slack
) {

    private static final String HTTPS_SCHEME = "https";
    private static final String SLACK_WEBHOOK_HOST = "hooks.slack.com";
    private static final String SLACK_WEBHOOK_PATH_PREFIX = "/services/";

    public NotificationProperties {
        Objects.requireNonNull(adminWebBaseUrl, "관리자 웹 기본 주소가 필요합니다.");
        validatePositive(pollInterval, "알림 조회 주기는 0보다 커야 합니다.");
        validatePositive(processingLease, "알림 처리 lease는 0보다 커야 합니다.");
        validatePositive(initialRetryDelay, "알림 최초 재시도 간격은 0보다 커야 합니다.");
        validatePositive(maxRetryDelay, "알림 최대 재시도 간격은 0보다 커야 합니다.");
        Objects.requireNonNull(slack, "Slack 알림 설정이 필요합니다.");

        if (batchSize <= 0) {
            throw new IllegalArgumentException("알림 처리 묶음 크기는 0보다 커야 합니다.");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("알림 최대 시도 횟수는 0보다 커야 합니다.");
        }
        if (maxRetryDelay.compareTo(initialRetryDelay) < 0) {
            throw new IllegalArgumentException(
                    "알림 최대 재시도 간격은 최초 재시도 간격보다 짧을 수 없습니다."
            );
        }
        Duration maximumHttpWait = slack.connectTimeout().plus(slack.readTimeout());
        if (processingLease.compareTo(maximumHttpWait) <= 0) {
            throw new IllegalArgumentException(
                    "알림 처리 lease는 Slack HTTP 최대 대기 시간보다 길어야 합니다."
            );
        }
        if (deliveryEnabled && !isExactHttpsOrigin(adminWebBaseUrl)) {
            throw new IllegalArgumentException(
                    "관리자 웹 기본 주소는 경로 없는 HTTPS Origin이어야 합니다."
            );
        }
        if (deliveryEnabled && !isSlackWebhook(slack.webhookUrl())) {
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

    private static boolean isExactHttpsOrigin(URI uri) {
        if (!HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return false;
        }
        if (uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            return false;
        }
        String path = uri.getRawPath();
        return path == null || path.isEmpty();
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
                .split("/");
        return pathSegments.length == 3
                && !pathSegments[0].isBlank()
                && !pathSegments[1].isBlank()
                && !pathSegments[2].isBlank();
    }

    public record Slack(
            URI webhookUrl,
            Duration connectTimeout,
            Duration readTimeout
    ) {

        public Slack {
            Objects.requireNonNull(webhookUrl, "Slack Webhook URL이 필요합니다.");
            validatePositive(connectTimeout, "Slack 연결 제한 시간은 0보다 커야 합니다.");
            validatePositive(readTimeout, "Slack 응답 제한 시간은 0보다 커야 합니다.");
        }
    }
}
