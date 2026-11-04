package com.chalkak.backend.admin.service.feedback;

import com.chalkak.backend.admin.repository.feedback.AdminFeedbackDetailProjection;
import com.chalkak.backend.admin.repository.feedback.AdminFeedbackProjection;
import com.chalkak.backend.admin.service.feedback.AdminFeedbackListResult.AuthorSummary;
import java.time.Instant;
import java.util.UUID;

public record AdminFeedbackDetailResult(
        UUID feedbackId,
        String content,
        Instant createdAt,
        String answerContent,
        Instant answeredAt,
        Instant deletedAt,
        AuthorSummary author
) {

    public static AdminFeedbackDetailResult from(AdminFeedbackDetailProjection detail) {
        AdminFeedbackProjection feedback = detail.feedback();
        return new AdminFeedbackDetailResult(
                feedback.feedbackId(),
                feedback.content(),
                feedback.createdAt(),
                detail.answerContent(),
                detail.answeredAt(),
                feedback.deletedAt(),
                AuthorSummary.from(feedback)
        );
    }
}
