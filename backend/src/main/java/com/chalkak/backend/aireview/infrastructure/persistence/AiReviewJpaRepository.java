package com.chalkak.backend.aireview.infrastructure.persistence;

import com.chalkak.backend.aireview.domain.AiReview;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiReviewJpaRepository extends JpaRepository<AiReview, UUID> {

    // 네이티브로 넣으므로 영속성 컨텍스트가 비어 있는 상태로 이어서 읽도록 비운다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO ai_reviews (post_id, user_id, prompt_version_id, status, attempt,
                request_id, requested_at, last_requested_at)
            VALUES (:postId, :userId, :promptVersionId, :status, :attempt,
                :requestId, :requestedAt, :lastRequestedAt)
            ON CONFLICT (post_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("postId") UUID postId,
            @Param("userId") UUID userId,
            @Param("promptVersionId") UUID promptVersionId,
            @Param("status") String status,
            @Param("attempt") int attempt,
            @Param("requestId") UUID requestId,
            @Param("requestedAt") Instant requestedAt,
            @Param("lastRequestedAt") Instant lastRequestedAt
    );

    Optional<AiReview> findByPostId(UUID postId);

    @Query(value = """
            SELECT * FROM ai_reviews
            WHERE (status = 'QUEUED' AND (next_attempt_at IS NULL OR next_attempt_at <= :now))
                OR (status = 'PROCESSING' AND lease_expires_at < :now)
            ORDER BY COALESCE(next_attempt_at, last_requested_at), id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<AiReview> findClaimableForUpdate(@Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT review FROM AiReview review WHERE review.id = :id")
    Optional<AiReview> findByIdForUpdate(@Param("id") UUID id);
}
