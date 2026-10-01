package com.chalkak.backend.admin.repository.feedback;

import com.chalkak.backend.admin.repository.AdminPage;
public interface AdminFeedbackQueryRepository {

    AdminPage<AdminFeedbackSummaryProjection> findFeedbacks(
            AdminFeedbackSort sort,
            int page,
            int pageSize
    );
}
