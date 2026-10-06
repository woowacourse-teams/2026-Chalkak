package com.chalkak.backend.notification.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
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

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    private static final Duration RETENTION = Duration.ofDays(30);
    private static final String APPROVED_TITLE = "게시물이 승인되었습니다.";
    private static final String APPROVED_BODY = "내 사진이 피드에 공개되었습니다.";
    private static final String REJECTED_TITLE = "게시물이 반려되었습니다.";
    private static final String REJECTED_BODY = "반려 사유를 확인해 주세요.";
    private static final int MAX_REJECTION_REASON_LENGTH = 500;

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", updatable = false, length = 32)
    private NotificationSourceType sourceType;

    @Column(name = "source_id", updatable = false)
    private UUID sourceId;

    @Column(name = "event_key", nullable = false, updatable = false)
    private UUID eventKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 32)
    private NotificationType type;

    @Column(name = "title", nullable = false, updatable = false, columnDefinition = "text")
    private String title;

    @Column(name = "body", nullable = false, updatable = false, columnDefinition = "text")
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", updatable = false, columnDefinition = "jsonb")
    private NotificationPayload payload;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Instant getRetentionThreshold(Instant now) {
        return now.minus(RETENTION);
    }

    public static Notification approved(
            UUID userId,
            UUID postId,
            UUID eventKey,
            Instant createdAt
    ) {
        validateRequired(userId, postId, eventKey, createdAt);
        Notification notification = new Notification();
        notification.userId = userId;
        notification.sourceType = NotificationSourceType.POST;
        notification.sourceId = postId;
        notification.eventKey = eventKey;
        notification.type = NotificationType.POST_APPROVED;
        notification.title = APPROVED_TITLE;
        notification.body = APPROVED_BODY;
        notification.createdAt = createdAt;
        return notification;
    }

    public static Notification rejected(
            UUID userId,
            UUID postId,
            UUID eventKey,
            String rejectionReason,
            Instant createdAt
    ) {
        validateRequired(userId, postId, eventKey, createdAt);
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "반려 알림에는 사유가 필요합니다.");
        }
        if (rejectionReason.codePointCount(0,
                rejectionReason.length()) > MAX_REJECTION_REASON_LENGTH) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "반려 사유는 500자 이하여야 합니다.");
        }
        Notification notification = new Notification();
        notification.userId = userId;
        notification.sourceType = NotificationSourceType.POST;
        notification.sourceId = postId;
        notification.eventKey = eventKey;
        notification.type = NotificationType.POST_REJECTED;
        notification.title = REJECTED_TITLE;
        notification.body = REJECTED_BODY;
        notification.payload = new NotificationPayload(rejectionReason);
        notification.createdAt = createdAt;
        return notification;
    }

    private static void validateRequired(
            UUID userId,
            UUID postId,
            UUID eventKey,
            Instant createdAt
    ) {
        if (userId == null || postId == null || eventKey == null || createdAt == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "알림 생성 정보가 올바르지 않습니다.");
        }
    }

    public String getRejectionReason() {
        if (type != NotificationType.POST_REJECTED) {
            return null;
        }
        return payload.rejectionReason();
    }
}
