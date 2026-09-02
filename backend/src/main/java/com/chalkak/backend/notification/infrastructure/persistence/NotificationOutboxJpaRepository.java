package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.NotificationOutbox;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface NotificationOutboxJpaRepository extends Repository<NotificationOutbox, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO notification_outboxes (
                post_id, type, channel, target, status,
                attempt_count, next_attempt_at, created_at, updated_at
            ) VALUES (
                :postId,
                CAST('POST_MODERATION_PENDING' AS notification_type),
                CAST('SLACK' AS notification_channel),
                CAST('ADMIN_MODERATION_REVIEWERS' AS notification_target),
                CAST('PENDING' AS notification_outbox_status),
                0, :createdAt, :createdAt, :createdAt
            )
            ON CONFLICT (post_id, type, channel, target) DO NOTHING
            """, nativeQuery = true)
    int createPostModerationPending(
            @Param("postId") UUID postId,
            @Param("createdAt") Instant createdAt
    );

    @Query(value = """
            SELECT outbox.*
            FROM notification_outboxes outbox
            WHERE (
                    outbox.status IN ('PENDING', 'RETRY')
                    AND outbox.next_attempt_at <= :now
                )
                OR (
                    outbox.status = 'PROCESSING'
                    AND outbox.lease_expires_at <= :now
                )
            ORDER BY
                CASE
                    WHEN outbox.status = 'PROCESSING' THEN outbox.lease_expires_at
                    ELSE outbox.next_attempt_at
                END ASC,
                outbox.id ASC
            FOR UPDATE SKIP LOCKED
            LIMIT :batchSize
            """, nativeQuery = true)
    List<NotificationOutbox> findClaimableForUpdate(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE notification_outboxes
            SET status = 'SENT',
                next_attempt_at = NULL,
                processing_token = NULL,
                lease_expires_at = NULL,
                updated_at = :completedAt
            WHERE id = :outboxId
              AND status = 'PROCESSING'
              AND processing_token = :processingToken
              AND attempt_count = :attemptCount
            """, nativeQuery = true)
    int markSent(
            @Param("outboxId") UUID outboxId,
            @Param("processingToken") UUID processingToken,
            @Param("attemptCount") int attemptCount,
            @Param("completedAt") Instant completedAt
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE notification_outboxes
            SET status = 'RETRY',
                next_attempt_at = :nextAttemptAt,
                processing_token = NULL,
                lease_expires_at = NULL,
                last_error = :failureCode,
                updated_at = :failedAt
            WHERE id = :outboxId
              AND status = 'PROCESSING'
              AND processing_token = :processingToken
              AND attempt_count = :attemptCount
            """, nativeQuery = true)
    int markRetry(
            @Param("outboxId") UUID outboxId,
            @Param("processingToken") UUID processingToken,
            @Param("attemptCount") int attemptCount,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("failureCode") String failureCode,
            @Param("failedAt") Instant failedAt
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE notification_outboxes
            SET status = 'FAILED',
                next_attempt_at = NULL,
                processing_token = NULL,
                lease_expires_at = NULL,
                last_error = :failureCode,
                updated_at = :failedAt
            WHERE id = :outboxId
              AND status = 'PROCESSING'
              AND processing_token = :processingToken
              AND attempt_count = :attemptCount
            """, nativeQuery = true)
    int markFailed(
            @Param("outboxId") UUID outboxId,
            @Param("processingToken") UUID processingToken,
            @Param("attemptCount") int attemptCount,
            @Param("failureCode") String failureCode,
            @Param("failedAt") Instant failedAt
    );
}
