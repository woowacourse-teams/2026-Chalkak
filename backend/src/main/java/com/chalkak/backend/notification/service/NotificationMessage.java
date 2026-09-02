package com.chalkak.backend.notification.service;

import java.net.URI;
import java.time.Instant;

/**
 * Slack Block Kit 같은 공급자별 표현을 포함하지 않는 공통 메시지다.
 */
public record NotificationMessage(
        String title,
        String body,
        String actionLabel,
        URI actionUri,
        Instant occurredAt
) {
}
