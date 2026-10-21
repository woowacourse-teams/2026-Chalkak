package com.chalkak.backend.notification.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
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

    private static final Duration PUBLISH_RETRY_DELAY = Duration.ofMinutes(1);
    private static final Duration RETENTION = Duration.ofDays(30);
    private static final String APPROVED_TITLE = "게시물이 승인되었습니다.";
    private static final String APPROVED_BODY = "내 사진이 피드에 공개되었습니다.";
    private static final String REJECTED_TITLE = "게시물이 반려되었습니다.";
    private static final String REJECTED_BODY = "반려 사유를 확인해 주세요.";
    private static final String AI_REVIEW_COMPLETED_TITLE = "AI 피드백이 도착했어요";
    private static final String AI_REVIEW_COMPLETED_BODY = "사진의 구도 점수와 피드백을 확인해 보세요.";
    private static final String AI_REVIEW_FAILED_TITLE = "AI 피드백을 만들지 못했어요";
    private static final String AI_REVIEW_RETRYABLE_BODY = "잠시 후 다시 요청해 주세요.";
    private static final String AI_REVIEW_EXHAUSTED_BODY = "이 사진은 더 이상 AI 피드백을 요청할 수 없어요.";
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

    @Enumerated(EnumType.STRING)
    @Column(name = "sqs_publish_status", nullable = false, length = 20)
    private PublishStatus publishStatus;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "sqs_published_at")
    private Instant publishedAt;

    public static Instant getRetentionThreshold(Instant now) {
        return now.minus(RETENTION);
    }

    public static Notification approved(
            UUID userId,
            UUID postId,
            UUID eventKey,
            Instant createdAt,
            boolean pushEnabled
    ) {
        validateRequired(
                userId,
                postId,
                eventKey,
                createdAt
        );
        Notification notification = new Notification();
        notification.userId = userId;
        notification.sourceType = NotificationSourceType.POST;
        notification.sourceId = postId;
        notification.eventKey = eventKey;
        notification.type = NotificationType.POST_APPROVED;
        notification.title = APPROVED_TITLE;
        notification.body = APPROVED_BODY;
        notification.createdAt = createdAt;
        notification.initializePublication(pushEnabled);
        return notification;
    }

    public static Notification rejected(
            UUID userId,
            UUID postId,
            UUID eventKey,
            String rejectionReason,
            Instant createdAt,
            boolean pushEnabled
    ) {
        validateRequired(
                userId,
                postId,
                eventKey,
                createdAt
        );
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
        notification.initializePublication(pushEnabled);
        return notification;
    }

    public static Notification aiReviewCompleted(
            UUID userId,
            UUID postId,
            UUID aiReviewId,
            int round,
            Instant createdAt,
            boolean pushEnabled
    ) {
        return createAiReview(
                userId,
                postId,
                aiReviewId,
                round,
                NotificationType.AI_REVIEW_COMPLETED,
                AI_REVIEW_COMPLETED_TITLE,
                AI_REVIEW_COMPLETED_BODY,
                createdAt,
                pushEnabled
        );
    }

    public static Notification aiReviewFailed(
            UUID userId,
            UUID postId,
            UUID aiReviewId,
            int round,
            boolean canRequestAgain,
            Instant createdAt,
            boolean pushEnabled
    ) {
        return createAiReview(
                userId,
                postId,
                aiReviewId,
                round,
                NotificationType.AI_REVIEW_FAILED,
                AI_REVIEW_FAILED_TITLE,
                canRequestAgain ? AI_REVIEW_RETRYABLE_BODY : AI_REVIEW_EXHAUSTED_BODY,
                createdAt,
                pushEnabled
        );
    }

    public void markPublished(Instant acceptedAt) {
        validatePendingPublication();
        if (acceptedAt == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "SQS 수락 시각이 필요합니다.");
        }
        publishStatus = PublishStatus.PUBLISHED;
        publishedAt = acceptedAt;
        nextAttemptAt = null;
    }

    public void retryPublication(Instant attemptedAt) {
        validatePendingPublication();
        if (isPushExpired(attemptedAt)) {
            expirePublication();
            return;
        }
        Instant retryAt = attemptedAt.plus(PUBLISH_RETRY_DELAY);
        if (retryAt.isBefore(getPushExpiresAt())) {
            nextAttemptAt = retryAt;
            return;
        }
        nextAttemptAt = getPushExpiresAt();
    }

    public void expirePublication() {
        validatePendingPublication();
        publishStatus = PublishStatus.EXPIRED;
        nextAttemptAt = null;
    }

    public void failPublication() {
        validatePendingPublication();
        publishStatus = PublishStatus.FAILED;
        nextAttemptAt = null;
    }

    public boolean isPushExpired(Instant now) {
        return PushDeadlinePolicy.isExpiredAt(getPushExpiresAt(), now);
    }

    private void validatePendingPublication() {
        if (publishStatus != PublishStatus.PENDING) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "발행 대기 중인 알림만 처리할 수 있습니다.");
        }
    }

    private static Notification createAiReview(
            UUID userId,
            UUID postId,
            UUID aiReviewId,
            int round,
            NotificationType type,
            String title,
            String body,
            Instant createdAt,
            boolean pushEnabled
    ) {
        validateRequired(
                userId,
                postId,
                aiReviewId,
                createdAt
        );
        Notification notification = new Notification();
        notification.userId = userId;
        notification.sourceType = NotificationSourceType.POST;
        notification.sourceId = postId;
        notification.eventKey = createAiReviewEventKey(aiReviewId, round);
        notification.type = type;
        notification.title = title;
        notification.body = body;
        notification.createdAt = createdAt;
        notification.initializePublication(pushEnabled);
        return notification;
    }

    // 같은 리뷰의 같은 회차는 항상 같은 키가 되어 회차마다 알림이 한 건만 생긴다.
    private static UUID createAiReviewEventKey(UUID aiReviewId, int round) {
        return UUID.nameUUIDFromBytes(
                ("AI_REVIEW:" + aiReviewId + ":" + round).getBytes(StandardCharsets.UTF_8));
    }

    private void initializePublication(boolean pushEnabled) {
        if (!pushEnabled) {
            publishStatus = PublishStatus.NOT_REQUIRED;
            return;
        }
        publishStatus = PublishStatus.PENDING;
        nextAttemptAt = createdAt;
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

    public Instant getPushExpiresAt() {
        return PushDeadlinePolicy.calculateExpiresAt(createdAt);
    }

    public String getRejectionReason() {
        if (type != NotificationType.POST_REJECTED) {
            return null;
        }
        return payload.rejectionReason();
    }
}
