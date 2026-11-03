package com.chalkak.backend.admin.service.feedback;

import com.chalkak.backend.admin.repository.feedback.AdminFeedbackSummaryProjection;
import com.chalkak.backend.admin.service.feedback.AdminFeedbackListResult.AuthorSummary;
import java.time.Instant;
import java.util.UUID;

public record AdminFeedbackDetailResult(
        UUID feedbackId,
        String content,
        Instant createdAt,
        Instant deletedAt,
        AuthorSummary author
) {

    public static AdminFeedbackDetailResult from(AdminFeedbackSummaryProjection feedback) {
        return new AdminFeedbackDetailResult(
                feedback.feedbackId(),
                feedback.content(),
                feedback.createdAt(),
                feedback.deletedAt(),
                AuthorSummary.from(feedback)
        );
    }
}
