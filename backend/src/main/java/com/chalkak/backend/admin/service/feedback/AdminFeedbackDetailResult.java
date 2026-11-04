package com.chalkak.backend.admin.service.feedback;

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

    public static AdminFeedbackDetailResult from(AdminFeedbackProjection feedback) {
        return new AdminFeedbackDetailResult(
                feedback.feedbackId(),
                feedback.content(),
                feedback.createdAt(),
                feedback.answerContent(),
                feedback.answeredAt(),
                feedback.deletedAt(),
                AuthorSummary.from(feedback)
        );
    }
}
