package com.chalkak.backend.notification.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 선점 트랜잭션이 끝난 뒤 외부 발송에 필요한 불변 정보다. JPA 엔티티를 트랜잭션 밖으로 넘기지 않는다.
 */
public record NotificationDispatch(
        UUID outboxId,
        UUID postId,
        NotificationType type,
        NotificationChannel channel,
        NotificationTarget target,
        int attemptCount,
        UUID processingToken,
        Instant occurredAt
) {
}
