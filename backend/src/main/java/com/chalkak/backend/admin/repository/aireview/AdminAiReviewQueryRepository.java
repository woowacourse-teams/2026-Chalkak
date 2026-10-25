package com.chalkak.backend.admin.repository.aireview;

import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.aireview.domain.AiReview;
import com.chalkak.backend.aireview.domain.AiReviewResult;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.user.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;

public interface AdminAiReviewQueryRepository {

    AdminPage<SummaryProjection> findAiReviews(
            Criteria criteria,
            int page,
            int pageSize
    );

    record Criteria(
            AiReview.Status status,
            UUID promptVersionId
    ) {
    }

    record SummaryProjection(
            UUID aiReviewId,
            AiReview.Status status,
            String errorCode,
            int requestCount,
            AiReviewResult result,
            UUID promptVersionId,
            int promptVersion,
            String promptModel,
            UUID postId,
            String thumbnailStorageKey,
            ModerationStatus moderationStatus,
            Instant postDeletedAt,
            UUID authorId,
            String authorEmail,
            UserStatus authorStatus,
            Instant authorDeletedAt,
            Integer inputTokens,
            Integer cachedInputTokens,
            Integer outputTokens,
            Integer reasoningTokens,
            Long latencyMs,
            Instant requestedAt,
            Instant lastRequestedAt,
            Instant completedAt,
            boolean visibleToAuthor
    ) {
    }
}
