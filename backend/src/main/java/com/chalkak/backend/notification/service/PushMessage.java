package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.domain.NotificationType;
import java.time.Instant;
import java.util.UUID;

public record PushMessage(
        UUID eventId,
        UUID notificationId,
        UUID userId,
        NotificationType type,
        NotificationSourceType sourceType,
        UUID sourceId,
        Instant occurredAt,
        Instant expiresAt) {
}
