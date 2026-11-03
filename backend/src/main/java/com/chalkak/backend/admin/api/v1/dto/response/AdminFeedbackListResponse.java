package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.repository.user.AdminUserStatus;
import com.chalkak.backend.admin.service.feedback.AdminFeedbackListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminFeedbackListResponse(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<FeedbackResponse> feedbacks
) {

    public static AdminFeedbackListResponse from(AdminFeedbackListResult result) {
        return new AdminFeedbackListResponse(
                result.currentPage(),
                result.pageSize(),
                result.hasNext(),
                result.feedbacks().stream()
                        .map(FeedbackResponse::from)
                        .toList()
        );
    }

    @Schema(name = "AdminFeedbackListItem")
    public record FeedbackResponse(
            UUID feedbackId,
            String content,
            Instant createdAt,
            @Schema(description = "문의 삭제 시각. 삭제되지 않았으면 null", nullable = true)
            Instant deletedAt,
            AuthorResponse author
    ) {

        private static FeedbackResponse from(AdminFeedbackListResult.FeedbackSummary feedback) {
            return new FeedbackResponse(
                    feedback.feedbackId(),
                    feedback.content(),
                    feedback.createdAt(),
                    feedback.deletedAt(),
                    AuthorResponse.from(feedback.author())
            );
        }
    }

    @Schema(name = "AdminFeedbackListAuthor")
    public record AuthorResponse(
            UUID userId,
            @Schema(nullable = true)
            String email,
            AdminUserStatus status,
            @Schema(nullable = true)
            String appVersion
    ) {

        static AuthorResponse from(AdminFeedbackListResult.AuthorSummary author) {
            return new AuthorResponse(
                    author.userId(),
                    author.email(),
                    author.status(),
                    author.appVersion()
            );
        }
    }
}
