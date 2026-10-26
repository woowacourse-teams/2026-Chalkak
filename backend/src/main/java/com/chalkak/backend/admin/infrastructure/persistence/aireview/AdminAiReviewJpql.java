package com.chalkak.backend.admin.infrastructure.persistence.aireview;

/**
 * 목록과 상세 조회가 같은 열과 조인을 쓰도록 JPQL 조각을 한곳에 둔다. 어노테이션 값은 상수여야 해서 패키지 안에서만 보이는 상수로
 * 둔다. visibleToAuthor의 조건은 PostJpaRepository.findVisibleById와 같아야 한다.
 */
final class AdminAiReviewJpql {

    static final String SUMMARY_COLUMNS = """
                review.id,
                review.status,
                review.errorCode,
                review.requestCount,
                review.result,
                promptVersion.id,
                promptVersion.version,
                promptVersion.model,
                post.id,
                photo.thumbnailStorageKey,
                post.moderationStatus,
                post.deletedAt,
                author.id,
                author.email,
                author.status,
                author.deletedAt,
                review.invocation.inputTokens,
                review.invocation.cachedInputTokens,
                review.invocation.outputTokens,
                review.invocation.reasoningTokens,
                review.invocation.latencyMs,
                review.requestedAt,
                review.lastRequestedAt,
                review.completedAt,
                CASE WHEN post.moderationStatus = :#{T(com.chalkak.backend.post.domain.ModerationStatus).APPROVED}
                      AND post.deletedAt IS NULL
                      AND topic.deletedAt IS NULL
                      AND photo.deletedAt IS NULL
                      AND author.deletedAt IS NULL
                     THEN true ELSE false END
            """;

    static final String FROM_JOINS = """
            FROM AiReview review
            JOIN Post post ON post.id = review.postId
            JOIN post.topic topic
            JOIN post.photo photo
            JOIN post.author author
            JOIN AiPromptVersion promptVersion ON promptVersion.id = review.promptVersionId
            """;

    private AdminAiReviewJpql() {
    }
}
