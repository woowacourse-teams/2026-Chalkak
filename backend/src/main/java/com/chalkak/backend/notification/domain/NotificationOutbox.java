package com.chalkak.backend.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 업무 트랜잭션에서 생긴 알림을 외부 채널로 안전하게 넘기기 위한 Outbox다.
 */
@Entity
@Table(name = "notification_outboxes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationOutbox {

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private UUID postId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type", nullable = false, updatable = false, columnDefinition = "notification_type")
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "channel",
            nullable = false,
            updatable = false,
            columnDefinition = "notification_channel"
    )
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "target",
            nullable = false,
            updatable = false,
            columnDefinition = "notification_target"
    )
    private NotificationTarget target;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "status",
            nullable = false,
            columnDefinition = "notification_outbox_status"
    )
    private NotificationOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "processing_token")
    private UUID processingToken;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static NotificationOutbox createPostModerationPending(UUID postId, Instant createdAt) {
        if (postId == null || createdAt == null) {
            throw new IllegalArgumentException("알림 생성 정보가 올바르지 않습니다.");
        }
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.postId = postId;
        outbox.type = NotificationType.POST_MODERATION_PENDING;
        outbox.channel = NotificationChannel.SLACK;
        outbox.target = NotificationTarget.ADMIN_MODERATION_REVIEWERS;
        outbox.status = NotificationOutboxStatus.PENDING;
        outbox.nextAttemptAt = createdAt;
        outbox.createdAt = createdAt;
        outbox.updatedAt = createdAt;
        return outbox;
    }

    public NotificationDispatch claim(UUID token, Instant claimedAt, Duration lease) {
        validateClaim(token, claimedAt, lease);
        this.status = NotificationOutboxStatus.PROCESSING;
        this.attemptCount += 1;
        this.processingToken = token;
        this.leaseExpiresAt = claimedAt.plus(lease);
        this.nextAttemptAt = null;
        this.updatedAt = claimedAt;
        return new NotificationDispatch(
                id,
                postId,
                type,
                channel,
                target,
                attemptCount,
                processingToken,
                createdAt
        );
    }

    private void validateClaim(UUID token, Instant claimedAt, Duration lease) {
        if (token == null || claimedAt == null || lease == null || lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("알림 선점 정보가 올바르지 않습니다.");
        }
        if (status == NotificationOutboxStatus.PROCESSING
                && leaseExpiresAt != null
                && leaseExpiresAt.isAfter(claimedAt)) {
            throw new IllegalStateException("아직 처리 중인 알림은 다시 선점할 수 없습니다.");
        }
        if (status != NotificationOutboxStatus.PENDING
                && status != NotificationOutboxStatus.RETRY
                && status != NotificationOutboxStatus.PROCESSING) {
            throw new IllegalStateException("전송이 끝난 알림은 다시 선점할 수 없습니다.");
        }
    }
}
