package com.chalkak.backend.admin.repository.aireview;

import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.aireview.domain.AiReview;
import com.chalkak.backend.aireview.domain.AiReviewResult;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.user.domain.UserStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AdminAiReviewQueryRepository {

    AdminPage<SummaryProjection> findAiReviews(
            Criteria criteria,
            int page,
            int pageSize
    );

    Optional<DetailProjection> findAiReview(UUID aiReviewId);

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

    /**
     * 목록 항목 값(summary)에 상세에만 쓰는 값을 더한 조회 결과. JPQL 생성자 표현식은 중첩 객체를 만들 수 없어 목록 항목 값과
     * 같은 순서의 평평한 생성자를 따로 둔다.
     */
    record DetailProjection(
            SummaryProjection summary,
            String output,
            String openaiResponseId,
            int attempt,
            Instant startedAt,
            String originalStorageKey
    ) {

        public DetailProjection(
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
                boolean visibleToAuthor,
                String output,
                String openaiResponseId,
                int attempt,
                Instant startedAt,
                String originalStorageKey
        ) {
            this(
                    new SummaryProjection(
                            aiReviewId,
                            status,
                            errorCode,
                            requestCount,
                            result,
                            promptVersionId,
                            promptVersion,
                            promptModel,
                            postId,
                            thumbnailStorageKey,
                            moderationStatus,
                            postDeletedAt,
                            authorId,
                            authorEmail,
                            authorStatus,
                            authorDeletedAt,
                            inputTokens,
                            cachedInputTokens,
                            outputTokens,
                            reasoningTokens,
                            latencyMs,
                            requestedAt,
                            lastRequestedAt,
                            completedAt,
                            visibleToAuthor
                    ),
                    output,
                    openaiResponseId,
                    attempt,
                    startedAt,
                    originalStorageKey
            );
        }
    }
}
