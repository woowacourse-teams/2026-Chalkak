package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.NotificationType;
import java.time.Instant;
import java.util.UUID;

public record NotificationDetailResult(
        UUID id,
        NotificationType type,
        String title,
        String body,
        String originalImageUrl,
        String rejectionReason,
        Instant readAt,
        Instant createdAt) {
}
