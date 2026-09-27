package com.chalkak.backend.admin.infrastructure.persistence.feedback;

import com.chalkak.backend.admin.repository.feedback.AdminFeedbackSummaryProjection;
import com.chalkak.backend.feedback.domain.Feedback;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AdminFeedbackJpaRepository extends JpaRepository<Feedback, UUID> {

    @Query("""
            SELECT new com.chalkak.backend.admin.repository.feedback.AdminFeedbackSummaryProjection(
                feedback.id,
                feedback.content,
                feedback.createdAt,
                author.id,
                author.email,
                author.status,
                author.appVersion,
                author.deletedAt
            )
            FROM Feedback feedback
            JOIN User author ON author.id = feedback.userId
            """)
    Slice<AdminFeedbackSummaryProjection> findFeedbacks(
            Pageable pageable
    );
}
