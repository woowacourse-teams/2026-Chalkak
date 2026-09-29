package com.chalkak.backend.notification.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    private static final String APPROVED_TITLE = "게시물이 승인되었습니다.";
    private static final String APPROVED_BODY = "내 사진이 피드에 공개되었습니다.";
    private static final String REJECTED_TITLE = "게시물이 반려되었습니다.";
    private static final String REJECTED_BODY = "반려 사유를 확인해 주세요.";

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "post_id", nullable = false, updatable = false)
    private UUID postId;

    @Column(name = "event_key", nullable = false, updatable = false)
    private UUID eventKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 32)
    private NotificationType type;

    @Column(name = "title", nullable = false, updatable = false, columnDefinition = "text")
    private String title;

    @Column(name = "body", nullable = false, updatable = false, columnDefinition = "text")
    private String body;

    @Column(name = "rejection_reason", updatable = false, length = 500)
    private String rejectionReason;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Notification approved(
            UUID userId,
            UUID postId,
            UUID eventKey,
            Instant createdAt
    ) {
        validateRequired(userId, postId, eventKey, createdAt);
        Notification notification = new Notification();
        notification.userId = userId;
        notification.postId = postId;
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
        Notification notification = new Notification();
        notification.userId = userId;
        notification.postId = postId;
        notification.eventKey = eventKey;
        notification.type = NotificationType.POST_REJECTED;
        notification.title = REJECTED_TITLE;
        notification.body = REJECTED_BODY;
        notification.rejectionReason = rejectionReason;
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
}
