package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.NotificationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NotificationListResult(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<Summary> notifications) {

    public record Summary(
            UUID id,
            NotificationType type,
            String title,
            String body,
            String thumbnailImageUrl,
            Instant readAt,
            Instant createdAt) {
    }
}
