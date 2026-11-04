package com.chalkak.backend.feedback.infrastructure.persistence;

import com.chalkak.backend.feedback.domain.Feedback;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

public interface FeedbackJpaRepository extends JpaRepository<Feedback, UUID> {

    Slice<Feedback> findByUserIdAndDeletedAtIsNull(UUID userId, Pageable pageable);

    Optional<Feedback> findByIdAndUserIdAndDeletedAtIsNull(UUID feedbackId, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT feedback FROM Feedback feedback WHERE feedback.id = :feedbackId")
    Optional<Feedback> findByIdForUpdate(@Param("feedbackId") UUID feedbackId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT feedback FROM Feedback feedback WHERE feedback.id = :feedbackId AND feedback.userId = :userId")
    Optional<Feedback> findByIdAndUserIdForUpdate(
            @Param("feedbackId") UUID feedbackId,
            @Param("userId") UUID userId
    );

    @Query("""
            SELECT feedback.createdAt FROM Feedback feedback
            WHERE feedback.userId = :userId AND feedback.createdAt > :createdAfter
            ORDER BY feedback.createdAt DESC
            """)
    List<Instant> findSubmissionTimesAfter(
            @Param("userId") UUID userId,
            @Param("createdAfter") Instant createdAfter
    );
}
