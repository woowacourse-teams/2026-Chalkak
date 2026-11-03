package com.chalkak.backend.admin.infrastructure.persistence.feedback;

import com.chalkak.backend.admin.repository.feedback.AdminFeedbackSummaryProjection;
import com.chalkak.backend.feedback.domain.Feedback;
import java.util.UUID;
import java.util.Optional;
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
                feedback.deletedAt,
                author.id,
                author.email,
                author.status,
                author.appVersion,
                author.deletedAt
            )
            FROM Feedback feedback
            JOIN User author ON author.id = feedback.userId
            WHERE feedback.id = :feedbackId
            """)
    Optional<AdminFeedbackSummaryProjection> findFeedbackById(UUID feedbackId);

    @Query("""
            SELECT new com.chalkak.backend.admin.repository.feedback.AdminFeedbackSummaryProjection(
                feedback.id,
                feedback.content,
                feedback.createdAt,
                feedback.deletedAt,
                author.id,
                author.email,
                author.status,
                author.appVersion,
                author.deletedAt
            )
            FROM Feedback feedback
            JOIN User author ON author.id = feedback.userId
            WHERE feedback.userId = COALESCE(:userId, feedback.userId)
            """)
    Slice<AdminFeedbackSummaryProjection> findFeedbacks(
            UUID userId,
            Pageable pageable
    );
}
