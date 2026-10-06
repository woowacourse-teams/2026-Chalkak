package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.Notification;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    Notification save(Notification notification);

    NotificationSlice findByUserId(
            UUID userId,
            int page,
            int pageSize,
            Instant createdFrom
    );

    Optional<NotificationDetail> findDetailByIdAndUserId(
            UUID notificationId,
            UUID userId,
            Instant createdFrom
    );

    boolean existsUnreadByUserId(UUID userId, Instant createdFrom);

    int markRead(
            UUID notificationId,
            UUID userId,
            Instant readAt,
            Instant createdFrom
    );

    void markAllRead(
            UUID userId,
            Instant readAt,
            Instant createdFrom
    );

    void deleteExpiredForActiveUsers(Instant createdBefore);

    void deleteExpiredForWithdrawnUsers(Instant withdrawnBefore);
}
