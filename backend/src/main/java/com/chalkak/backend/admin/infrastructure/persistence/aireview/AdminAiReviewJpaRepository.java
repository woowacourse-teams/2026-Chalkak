package com.chalkak.backend.admin.infrastructure.persistence.aireview;

import com.chalkak.backend.admin.repository.aireview.AdminAiReviewQueryRepository.Criteria;
import com.chalkak.backend.admin.repository.aireview.AdminAiReviewQueryRepository.DetailProjection;
import com.chalkak.backend.admin.repository.aireview.AdminAiReviewQueryRepository.SummaryProjection;
import com.chalkak.backend.aireview.domain.AiReview;
import java.util.Optional;
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
    @Query("SELECT new com.chalkak.backend.admin.repository.aireview.AdminAiReviewQueryRepository$SummaryProjection("
            + AdminAiReviewJpql.SUMMARY_COLUMNS
            + ") "
            + AdminAiReviewJpql.FROM_JOINS
            + """
                    WHERE (:#{#criteria.status() == null} = true OR review.status = :#{#criteria.status()})
                      AND (:#{#criteria.promptVersionId() == null} = true OR promptVersion.id = :#{#criteria.promptVersionId()})
                    ORDER BY review.lastRequestedAt DESC, review.id DESC
                    """)
    Slice<SummaryProjection> findAiReviews(
            @Param("criteria") Criteria criteria,
            Pageable pageable
    );

    /**
     * 사용자 앱의 숨김 여부와 상관없이 리뷰 하나를 목록과 같은 열에 상세 열을 더해 조회한다.
     */
    @Query("SELECT new com.chalkak.backend.admin.repository.aireview.AdminAiReviewQueryRepository$DetailProjection("
            + AdminAiReviewJpql.SUMMARY_COLUMNS
            + """
                    ,
                        review.output,
                        review.invocation.openaiResponseId,
                        review.attempt,
                        review.startedAt,
                        photo.originalStorageKey
                    )
                    """
            + AdminAiReviewJpql.FROM_JOINS
            + "WHERE review.id = :aiReviewId")
    Optional<DetailProjection> findAiReview(@Param("aiReviewId") UUID aiReviewId);
}
