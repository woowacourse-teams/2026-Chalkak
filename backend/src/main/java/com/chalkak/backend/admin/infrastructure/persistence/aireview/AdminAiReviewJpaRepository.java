package com.chalkak.backend.admin.infrastructure.persistence.aireview;

import com.chalkak.backend.admin.repository.aireview.AdminAiReviewQueryRepository.Criteria;
import com.chalkak.backend.admin.repository.aireview.AdminAiReviewQueryRepository.SummaryProjection;
import com.chalkak.backend.aireview.domain.AiReview;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAiReviewJpaRepository extends JpaRepository<AiReview, UUID> {

    /**
     * 사용자 앱의 숨김 여부와 상관없이 모든 리뷰를 조회한다. 게시물·작성자·프롬프트 버전·사진은 FK가 RESTRICT라 항상 있으므로 내부
     * 조인한다. visibleToAuthor의 조건은 PostJpaRepository.findVisibleById와 같아야 한다.
     */
    @Query("""
            SELECT new com.chalkak.backend.admin.repository.aireview.AdminAiReviewQueryRepository$SummaryProjection(
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
            )
            FROM AiReview review
            JOIN Post post ON post.id = review.postId
            JOIN post.topic topic
            JOIN post.photo photo
            JOIN post.author author
            JOIN AiPromptVersion promptVersion ON promptVersion.id = review.promptVersionId
            WHERE (:#{#criteria.status() == null} = true OR review.status = :#{#criteria.status()})
              AND (:#{#criteria.promptVersionId() == null} = true OR promptVersion.id = :#{#criteria.promptVersionId()})
            ORDER BY review.lastRequestedAt DESC, review.id DESC
            """)
    Slice<SummaryProjection> findAiReviews(
            @Param("criteria") Criteria criteria,
            Pageable pageable
    );
}
