package com.chalkak.backend.admin.repository.feedback;

import com.chalkak.backend.admin.repository.AdminPage;
import java.util.UUID;
import java.util.Optional;
public interface AdminFeedbackQueryRepository {

    Optional<AdminFeedbackProjection> findFeedbackById(UUID feedbackId);

    AdminPage<AdminFeedbackProjection> findFeedbacks(
            UUID userId,
            AdminFeedbackSort sort,
            int page,
            int pageSize
    );
}
