package com.chalkak.backend.admin.service.feedback;

import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.admin.repository.feedback.AdminFeedbackSummaryProjection;
import com.chalkak.backend.admin.repository.user.AdminUserStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminFeedbackListResult(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<FeedbackSummary> feedbacks) {

    public static AdminFeedbackListResult from(AdminPage<AdminFeedbackSummaryProjection> page) {
        return new AdminFeedbackListResult(
                page.currentPage(),
                page.pageSize(),
                page.hasNext(),
                page.items().stream()
                        .map(FeedbackSummary::from)
                        .toList());
    }

    public record FeedbackSummary(
            UUID feedbackId,
            String content,
            Instant createdAt,
            AuthorSummary author) {

        private static FeedbackSummary from(AdminFeedbackSummaryProjection feedback) {
            return new FeedbackSummary(
                    feedback.feedbackId(),
                    feedback.content(),
                    feedback.createdAt(),
                    AuthorSummary.from(feedback));
        }
    }

    public record AuthorSummary(
            UUID userId,
            String email,
            AdminUserStatus status,
            String appVersion) {

        private static AuthorSummary from(AdminFeedbackSummaryProjection feedback) {
            return new AuthorSummary(
                    feedback.authorId(),
                    feedback.authorEmail(),
                    AdminUserStatus.from(feedback.authorStatus(), feedback.authorDeletedAt()),
                    feedback.authorAppVersion());
        }
    }
}
