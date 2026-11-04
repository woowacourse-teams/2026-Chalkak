package com.chalkak.backend.admin.service.feedback;

import com.chalkak.backend.admin.repository.feedback.AdminFeedbackDetailProjection;
import com.chalkak.backend.admin.repository.user.AdminUserStatus;
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
        return new AdminFeedbackDetailResult(
                detail.feedbackId(),
                detail.content(),
                detail.createdAt(),
                detail.answerContent(),
                detail.answeredAt(),
                detail.deletedAt(),
                new AuthorSummary(
                        detail.authorId(),
                        detail.authorEmail(),
                        AdminUserStatus.from(detail.authorStatus(), detail.authorDeletedAt()),
                        detail.authorAppVersion()
                )
        );
    }
}
