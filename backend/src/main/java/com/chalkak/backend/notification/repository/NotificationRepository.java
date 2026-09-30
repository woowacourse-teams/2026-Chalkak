package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.Notification;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    Notification save(Notification notification);

    NotificationSlice findByUserId(UUID userId, int page, int pageSize);

    Optional<NotificationDetail> findDetailByIdAndUserId(UUID notificationId, UUID userId);

    boolean existsUnreadByUserId(UUID userId);

    int markRead(UUID notificationId, UUID userId, Instant readAt);

    void markAllRead(UUID userId, Instant readAt);

    void deleteExpiredForActiveUsers(Instant createdBefore);

    void deleteExpiredForWithdrawnUsers(Instant withdrawnBefore);
}
