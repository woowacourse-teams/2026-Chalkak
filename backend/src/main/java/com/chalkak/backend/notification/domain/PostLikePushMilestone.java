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
@Table(name = "post_like_push_milestones")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostLikePushMilestone {

    private static final long MILESTONE_INTERVAL = 5L;

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
}
