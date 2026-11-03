package com.chalkak.backend.admin.repository.feedback;

import com.chalkak.backend.admin.repository.AdminPage;
import java.util.UUID;
public interface AdminFeedbackQueryRepository {

    AdminPage<AdminFeedbackSummaryProjection> findFeedbacks(
            UUID userId,
            AdminFeedbackSort sort,
            int page,
            int pageSize
    );
}
