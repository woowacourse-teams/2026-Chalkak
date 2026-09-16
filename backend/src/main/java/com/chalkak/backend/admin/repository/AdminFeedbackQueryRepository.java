package com.chalkak.backend.admin.repository;

public interface AdminFeedbackQueryRepository {

    AdminFeedbackQueryPage findFeedbacks(
            AdminFeedbackQuerySort sort,
            int page,
            int pageSize
    );
}
