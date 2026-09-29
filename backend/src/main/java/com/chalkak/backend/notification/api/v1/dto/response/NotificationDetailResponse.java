package com.chalkak.backend.notification.api.v1.dto.response;

import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.service.NotificationDetailResult;
import java.time.Instant;
import java.util.UUID;

public record NotificationDetailResponse(
        UUID id,
        NotificationType type,
        String title,
        String body,
        String originalImageUrl,
        String rejectionReason,
        Instant readAt,
        Instant createdAt
) {

    public static NotificationDetailResponse from(NotificationDetailResult result) {
        return new NotificationDetailResponse(
                result.id(),
                result.type(),
                result.title(),
                result.body(),
                result.originalImageUrl(),
                result.rejectionReason(),
                result.readAt(),
                result.createdAt());
    }
}
