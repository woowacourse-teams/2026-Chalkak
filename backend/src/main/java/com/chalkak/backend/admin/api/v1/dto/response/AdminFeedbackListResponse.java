package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.AdminFeedbackListResult;
import com.chalkak.backend.admin.service.AdminUserStatus;
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
                        .toList());
    }

    @Schema(name = "AdminFeedbackListItem")
    public record FeedbackResponse(
            UUID feedbackId,
            String content,
            Instant createdAt,
            WriterResponse writer
    ) {

        private static FeedbackResponse from(AdminFeedbackListResult.FeedbackSummary feedback) {
            return new FeedbackResponse(
                    feedback.feedbackId(),
                    feedback.content(),
                    feedback.createdAt(),
                    WriterResponse.from(feedback.writer()));
        }
    }

    @Schema(name = "AdminFeedbackWriter")
    public record WriterResponse(
            UUID userId,
            @Schema(nullable = true)
            String email,
            AdminUserStatus status,
            @Schema(nullable = true)
            String appVersion
    ) {

        private static WriterResponse from(AdminFeedbackListResult.Writer writer) {
            return new WriterResponse(
                    writer.userId(),
                    writer.email(),
                    writer.status(),
                    writer.appVersion());
        }
    }
}
