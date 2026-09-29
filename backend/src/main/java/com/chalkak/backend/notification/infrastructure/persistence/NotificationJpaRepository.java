package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.Notification;
import com.chalkak.backend.notification.repository.NotificationDetail;
import com.chalkak.backend.notification.repository.NotificationSummary;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationJpaRepository extends JpaRepository<Notification, UUID> {

    @Query("""
            SELECT new com.chalkak.backend.notification.repository.NotificationSummary(
                notification, photo.thumbnailStorageKey)
            FROM Notification notification
            JOIN Post post ON post.id = notification.postId
            JOIN post.photo photo
            WHERE notification.userId = :userId
            ORDER BY notification.createdAt DESC, notification.id DESC
            """)
    Slice<NotificationSummary> findSummariesByUserId(
            @Param("userId") UUID userId,
            Pageable pageable
    );

    @Query("""
            SELECT new com.chalkak.backend.notification.repository.NotificationDetail(
                notification, photo.originalStorageKey)
            FROM Notification notification
            JOIN Post post ON post.id = notification.postId
            JOIN post.photo photo
            WHERE notification.id = :notificationId AND notification.userId = :userId
            """)
    Optional<NotificationDetail> findDetailByIdAndUserId(
            @Param("notificationId") UUID notificationId,
            @Param("userId") UUID userId
    );

    boolean existsByUserIdAndReadAtIsNull(UUID userId);

    @Modifying
    @Query("""
            UPDATE Notification notification
            SET notification.readAt = coalesce(notification.readAt, :readAt)
            WHERE notification.id = :notificationId AND notification.userId = :userId
            """)
    int markRead(
            @Param("notificationId") UUID notificationId,
            @Param("userId") UUID userId,
            @Param("readAt") Instant readAt
    );

    @Modifying
    @Query("""
            UPDATE Notification notification
            SET notification.readAt = :readAt
            WHERE notification.userId = :userId AND notification.readAt IS NULL
            """)
    int markAllRead(
            @Param("userId") UUID userId,
            @Param("readAt") Instant readAt
    );
}
