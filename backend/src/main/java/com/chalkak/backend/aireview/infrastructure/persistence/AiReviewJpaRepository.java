package com.chalkak.backend.aireview.infrastructure.persistence;

import com.chalkak.backend.aireview.domain.AiReview;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiReviewJpaRepository extends JpaRepository<AiReview, UUID> {

    // 네이티브로 넣으므로 영속성 컨텍스트가 비어 있는 상태로 이어서 읽도록 비운다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = """
                    INSERT INTO ai_reviews (post_id, user_id, prompt_version_id, status, attempt,
                        request_id, requested_at)
                    VALUES (:postId, :userId, :promptVersionId, :status, :attempt,
                        :requestId, :requestedAt)
                    ON CONFLICT (post_id) DO NOTHING
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("postId") UUID postId,
            @Param("userId") UUID userId,
            @Param("promptVersionId") UUID promptVersionId,
            @Param("status") String status,
            @Param("attempt") int attempt,
            @Param("requestId") UUID requestId,
            @Param("requestedAt") Instant requestedAt
    );

    Optional<AiReview> findByPostId(UUID postId);
}
