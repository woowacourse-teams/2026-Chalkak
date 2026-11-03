package com.chalkak.backend.feedback.api.v1.dto.response;

import com.chalkak.backend.feedback.service.FeedbackListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FeedbackListResponse(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<FeedbackResponse> feedbacks
) {

    public static FeedbackListResponse from(FeedbackListResult result) {
        return new FeedbackListResponse(
                result.currentPage(),
                result.pageSize(),
                result.hasNext(),
                result.feedbacks().stream().map(FeedbackResponse::from).toList()
        );
    }

    @Schema(name = "FeedbackListItem")
    public record FeedbackResponse(
            UUID feedbackId,
            String content,
            Instant createdAt
    ) {

        private static FeedbackResponse from(FeedbackListResult.FeedbackSummary feedback) {
            return new FeedbackResponse(
                    feedback.feedbackId(),
                    feedback.content(),
                    feedback.createdAt()
            );
        }
    }
}
