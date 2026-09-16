package com.chalkak.backend.admin.repository;

import java.util.List;

public record AdminFeedbackQueryPage(
        List<AdminFeedbackSummaryProjection> feedbacks,
        int currentPage,
        int pageSize,
        boolean hasNext
) {
}
