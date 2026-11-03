package com.chalkak.backend.feedback.infrastructure.persistence;

import com.chalkak.backend.feedback.domain.Feedback;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedbackJpaRepository extends JpaRepository<Feedback, UUID> {

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
