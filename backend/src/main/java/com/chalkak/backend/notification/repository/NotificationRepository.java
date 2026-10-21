package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.Notice;
import com.chalkak.backend.notification.domain.Notification;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository {

    PublicationBacklog getPublicationBacklog();

    List<UUID> findTopicPushRecipientIds(UUID topicId);

    List<UUID> findDuePublicationIds(Instant now, int limit);

    Optional<Notification> findPendingPublicationForUpdate(UUID notificationId, Instant now);

    Optional<Notification> findForPushByIdAndUserId(UUID notificationId, UUID userId);

    Notification save(Notification notification);

    /**
     * 같은 회원의 같은 사건 키가 이미 있으면 저장하지 않는다.
     *
     * @return 저장한 행 수. 이미 있으면 0
     */
    int insertIfAbsent(Notification notification);

    int createNoticeNotifications(Notice notice, Instant createdAt);

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
