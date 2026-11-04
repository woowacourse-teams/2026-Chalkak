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
        List<FeedbackListItem> feedbacks
) {

    public static FeedbackListResponse from(FeedbackListResult result) {
        return new FeedbackListResponse(
                result.currentPage(),
                result.pageSize(),
                result.hasNext(),
                result.feedbacks().stream().map(FeedbackListItem::from).toList()
        );
    }

    @Schema(name = "FeedbackListItem")
    public record FeedbackListItem(
            UUID feedbackId,
            String content,
            Instant createdAt
    ) {

        private static FeedbackListItem from(FeedbackListResult.FeedbackSummary feedback) {
            return new FeedbackListItem(
                    feedback.feedbackId(),
                    feedback.content(),
                    feedback.createdAt()
            );
        }
    }
}
