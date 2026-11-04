package com.chalkak.backend.admin.service.feedback;

import com.chalkak.backend.admin.repository.feedback.AdminFeedbackDetailProjection;
import com.chalkak.backend.admin.repository.user.AdminUserStatus;
import com.chalkak.backend.admin.service.feedback.AdminFeedbackListResult.AuthorSummary;
import com.chalkak.backend.feedback.domain.Feedback;
import java.time.Instant;
import java.util.UUID;

public record AdminFeedbackDetailResult(
        UUID feedbackId,
        String content,
        Instant createdAt,
        String answerContent,
        Instant answeredAt,
        AnswerState answerState,
        String answerVersion,
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
                AnswerState.from(detail),
                detail.answeredAt() == null
                        ? null
                        : Feedback.createAnswerVersion(detail.answeredAt()),
                detail.deletedAt(),
                new AuthorSummary(
                        detail.authorId(),
                        detail.authorEmail(),
                        AdminUserStatus.from(detail.authorStatus(), detail.authorDeletedAt()),
                        detail.authorAppVersion()
                )
        );
    }

    public enum AnswerState {
        UNANSWERED, ANSWERED, DELETED;

        private static AnswerState from(AdminFeedbackDetailProjection detail) {
            if (detail.answerDeletedAt() != null) {
                return DELETED;
            }
            return detail.answeredAt() == null ? UNANSWERED : ANSWERED;
        }
    }
}
