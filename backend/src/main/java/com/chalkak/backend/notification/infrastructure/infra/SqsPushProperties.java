package com.chalkak.backend.notification.infrastructure.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chalkak.notification.sqs")
public record SqsPushProperties(boolean relayEnabled, String queueUrl, String region) {

    public SqsPushProperties {
        if (relayEnabled) {
            validateQueueUrl(queueUrl);
        }
    }

    public void validateQueueUrl() {
        validateQueueUrl(queueUrl);
    }

    private static void validateQueueUrl(String queueUrl) {
        if (queueUrl == null || !queueUrl.matches(
                "https://sqs\\.[a-z0-9-]+\\.amazonaws\\.com/[0-9]{12}/[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("알림 푸시를 사용하려면 Standard SQS 큐 URL이 필요합니다.");
        }
    }
}
