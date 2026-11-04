package com.chalkak.backend.admin.infrastructure.persistence.feedback;

import com.chalkak.backend.admin.repository.feedback.AdminFeedbackDetailProjection;
import com.chalkak.backend.admin.repository.feedback.AdminFeedbackProjection;
import com.chalkak.backend.feedback.domain.Feedback;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminFeedbackJpaRepository extends JpaRepository<Feedback, UUID> {

    @Query(AdminFeedbackJpql.SELECT_DETAIL_WITH_AUTHOR + " WHERE feedback.id = :feedbackId")
    Optional<AdminFeedbackDetailProjection> findFeedbackById(@Param("feedbackId") UUID feedbackId);

    @Query(AdminFeedbackJpql.SELECT_WITH_AUTHOR)
    Slice<AdminFeedbackProjection> findFeedbacks(Pageable pageable);

    @Query(AdminFeedbackJpql.SELECT_WITH_AUTHOR + " WHERE feedback.userId = :userId")
    Slice<AdminFeedbackProjection> findFeedbacksByUserId(
            @Param("userId") UUID userId,
            Pageable pageable
    );
}
