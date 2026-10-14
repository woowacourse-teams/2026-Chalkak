package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.Notification;
import com.chalkak.backend.notification.repository.NotificationDetail;
import com.chalkak.backend.notification.repository.NotificationSummary;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.user.domain.UserStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationJpaRepository extends JpaRepository<Notification, UUID> {

    @Query("""
            SELECT user.id FROM User user
            WHERE user.deletedAt IS NULL
                AND user.status = :status
                AND user.topicPushEnabled = true
                AND NOT EXISTS (
                    SELECT post.id FROM Post post
                    WHERE post.author.id = user.id
                        AND post.topic.id = :topicId
                        AND post.deletedAt IS NULL
                        AND post.moderationStatus <> :excludedStatus
                )
            ORDER BY user.id
            """)
    List<UUID> findTopicPushRecipientIds(
            @Param("status") UserStatus status,
            @Param("topicId") UUID topicId,
            @Param("excludedStatus") ModerationStatus excludedStatus
    );

    // 탈퇴하지 않은 회원 전체(정지 회원 포함)에게 공지 알림을 한 번에 만든다. 공지 ID를 사건 키로 써서 회원당 하나만 생긴다.
    @Modifying
    @Query(value = """
            INSERT INTO notifications (user_id, source_type, source_id, event_key, type, title,
                body, created_at, sqs_publish_status, next_attempt_at)
            SELECT u.id, 'NOTICE', :noticeId, :noticeId, 'NOTICE', :title, :body, :createdAt,
                'PENDING', :createdAt
            FROM users u
            WHERE u.deleted_at IS NULL
            """, nativeQuery = true)
    int insertNoticeNotifications(
            @Param("noticeId") UUID noticeId,
            @Param("title") String title,
            @Param("body") String body,
            @Param("createdAt") Instant createdAt
    );

    @Query(value = """
            SELECT id FROM notifications
            WHERE sqs_publish_status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at, id
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findDuePublicationIds(@Param("now") Instant now, @Param("limit") int limit);

    @Query(value = """
            SELECT * FROM notifications
            WHERE id = :notificationId AND sqs_publish_status = 'PENDING'
                AND next_attempt_at <= :now
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<Notification> findPendingPublicationForUpdate(
            @Param("notificationId") UUID notificationId,
            @Param("now") Instant now
    );

    @Query("""
            SELECT notification FROM Notification notification
            LEFT JOIN Post post ON notification.sourceType = com.chalkak.backend.notification.domain.NotificationSourceType.POST
                AND post.id = notification.sourceId
            WHERE notification.id = :notificationId AND notification.userId = :userId
                AND (
                    notification.sourceType IS NULL
                    OR notification.sourceType <> com.chalkak.backend.notification.domain.NotificationSourceType.POST
                    OR (post.id IS NOT NULL AND post.deletedAt IS NULL)
                )
            """)
    Optional<Notification> findForPushByIdAndUserId(
            @Param("notificationId") UUID notificationId,
            @Param("userId") UUID userId
    );

    @Query("""
            SELECT new com.chalkak.backend.notification.repository.NotificationSummary(
                notification, photo.thumbnailStorageKey)
            FROM Notification notification
            LEFT JOIN Post post ON notification.sourceType = com.chalkak.backend.notification.domain.NotificationSourceType.POST
                AND post.id = notification.sourceId
            LEFT JOIN post.photo photo
            WHERE notification.userId = :userId
                AND notification.createdAt >= :createdFrom
                AND (
                    notification.sourceType IS NULL
                    OR notification.sourceType <> com.chalkak.backend.notification.domain.NotificationSourceType.POST
                    OR (post.id IS NOT NULL AND post.deletedAt IS NULL)
                )
            ORDER BY notification.createdAt DESC, notification.id DESC
            """)
    Slice<NotificationSummary> findSummariesByUserId(
            @Param("userId") UUID userId,
            @Param("createdFrom") Instant createdFrom,
            Pageable pageable
    );

    @Query("""
            SELECT new com.chalkak.backend.notification.repository.NotificationDetail(
                notification, photo.originalStorageKey)
            FROM Notification notification
            LEFT JOIN Post post ON notification.sourceType = com.chalkak.backend.notification.domain.NotificationSourceType.POST
                AND post.id = notification.sourceId
            LEFT JOIN post.photo photo
            WHERE notification.id = :notificationId AND notification.userId = :userId
                AND notification.createdAt >= :createdFrom
                AND (
                    notification.sourceType IS NULL
                    OR notification.sourceType <> com.chalkak.backend.notification.domain.NotificationSourceType.POST
                    OR (post.id IS NOT NULL AND post.deletedAt IS NULL)
                )
            """)
    Optional<NotificationDetail> findDetailByIdAndUserId(
            @Param("notificationId") UUID notificationId,
            @Param("userId") UUID userId,
            @Param("createdFrom") Instant createdFrom
    );

    @Query("""
            SELECT CASE WHEN COUNT(notification) > 0 THEN true ELSE false END
            FROM Notification notification
            LEFT JOIN Post post ON notification.sourceType = com.chalkak.backend.notification.domain.NotificationSourceType.POST
                AND post.id = notification.sourceId
            WHERE notification.userId = :userId AND notification.readAt IS NULL
                AND notification.createdAt >= :createdFrom
                AND (
                    notification.sourceType IS NULL
                    OR notification.sourceType <> com.chalkak.backend.notification.domain.NotificationSourceType.POST
                    OR (post.id IS NOT NULL AND post.deletedAt IS NULL)
                )
            """)
    boolean existsVisibleUnreadByUserId(
            @Param("userId") UUID userId,
            @Param("createdFrom") Instant createdFrom
    );

    @Modifying
    @Query("""
            UPDATE Notification notification
            SET notification.readAt = coalesce(notification.readAt, :readAt)
            WHERE notification.id = :notificationId AND notification.userId = :userId
                AND notification.createdAt >= :createdFrom
                AND (
                    notification.sourceType IS NULL
                    OR notification.sourceType <> com.chalkak.backend.notification.domain.NotificationSourceType.POST
                    OR EXISTS (
                        SELECT post.id FROM Post post
                        WHERE post.id = notification.sourceId
                            AND post.deletedAt IS NULL
                    )
                )
            """)
    int markRead(
            @Param("notificationId") UUID notificationId,
            @Param("userId") UUID userId,
            @Param("readAt") Instant readAt,
            @Param("createdFrom") Instant createdFrom
    );

    @Modifying
    @Query("""
            UPDATE Notification notification
            SET notification.readAt = :readAt
            WHERE notification.userId = :userId AND notification.readAt IS NULL
                AND notification.createdAt >= :createdFrom
                AND (
                    notification.sourceType IS NULL
                    OR notification.sourceType <> com.chalkak.backend.notification.domain.NotificationSourceType.POST
                    OR EXISTS (
                        SELECT post.id FROM Post post
                        WHERE post.id = notification.sourceId
                            AND post.deletedAt IS NULL
                    )
                )
            """)
    int markAllRead(
            @Param("userId") UUID userId,
            @Param("readAt") Instant readAt,
            @Param("createdFrom") Instant createdFrom
    );

    @Modifying
    @Query("""
            DELETE FROM Notification notification
            WHERE notification.createdAt < :createdBefore
                AND notification.userId IN (
                    SELECT user.id FROM User user WHERE user.deletedAt IS NULL
                )
            """)
    int deleteExpiredForActiveUsers(@Param("createdBefore") Instant createdBefore);

    @Modifying
    @Query("""
            DELETE FROM Notification notification
            WHERE notification.userId IN (
                SELECT user.id FROM User user WHERE user.deletedAt < :withdrawnBefore
            )
            """)
    int deleteExpiredForWithdrawnUsers(@Param("withdrawnBefore") Instant withdrawnBefore);
}
