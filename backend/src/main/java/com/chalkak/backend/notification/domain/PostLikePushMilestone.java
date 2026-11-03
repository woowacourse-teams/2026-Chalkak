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

@Entity
@Table(name = "post_like_push_milestones")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostLikePushMilestone {

    private static final long MILESTONE_INTERVAL = 5L;
    private static final Duration PUBLISH_RETRY_DELAY = Duration.ofMinutes(1);

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private UUID postId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "milestone_count", nullable = false, updatable = false)
    private long milestoneCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sqs_publish_status", nullable = false, length = 20)
    private PublishStatus publishStatus;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "sqs_published_at")
    private Instant publishedAt;

    public static PostLikePushMilestone create(
            UUID postId,
            UUID userId,
            long milestoneCount,
            Instant createdAt
    ) {
        validateRequired(
                postId,
                userId,
                createdAt
        );
        validateMilestoneCount(milestoneCount);

        PostLikePushMilestone milestone = new PostLikePushMilestone();
        milestone.postId = postId;
        milestone.userId = userId;
        milestone.milestoneCount = milestoneCount;
        milestone.createdAt = createdAt;
        milestone.publishStatus = PublishStatus.PENDING;
        milestone.nextAttemptAt = createdAt;
        return milestone;
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

    public Instant getPushExpiresAt() {
        return PushDeadlinePolicy.calculateExpiresAt(createdAt);
    }

    private static void validateRequired(Object... requiredValues) {
        for (Object value : requiredValues) {
            if (value == null) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "좋아요 마일스톤 생성 정보가 올바르지 않습니다.");
            }
        }
    }

    private static void validateMilestoneCount(long milestoneCount) {
        if (milestoneCount < MILESTONE_INTERVAL
                || milestoneCount % MILESTONE_INTERVAL != 0) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "좋아요 마일스톤은 5 이상의 5단위여야 합니다.");
        }
    }

    private void validatePendingPublication() {
        if (publishStatus != PublishStatus.PENDING) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "발행 대기 중인 좋아요 마일스톤만 처리할 수 있습니다.");
        }
    }
}
