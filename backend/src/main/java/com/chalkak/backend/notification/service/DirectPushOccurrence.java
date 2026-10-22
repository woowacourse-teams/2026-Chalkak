package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.domain.NotificationType;
import java.time.Instant;
import java.util.UUID;

// 알림함 없이 SQS로 직접 보내는 한 회차의 공통 값이다. 로그 단계 이름은 접두사로 구분한다.
public record DirectPushOccurrence(
        UUID eventId,
        NotificationType type,
        NotificationSourceType sourceType,
        UUID sourceId,
        Instant occurredAt,
        String stagePrefix
) {

    public PushMessage toMessage(UUID userId, Instant expiresAt) {
        return new PushMessage(
                eventId,
                null,
                userId,
                type,
                sourceType,
                sourceId,
                occurredAt,
                expiresAt
        );
    }
}
