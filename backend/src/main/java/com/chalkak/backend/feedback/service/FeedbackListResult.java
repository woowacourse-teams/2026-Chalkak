package com.chalkak.backend.feedback.service;

import com.chalkak.backend.feedback.domain.Feedback;
import com.chalkak.backend.feedback.repository.FeedbackSlice;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FeedbackListResult(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<FeedbackSummary> feedbacks
) {

    public static FeedbackListResult from(FeedbackSlice slice) {
        return new FeedbackListResult(
                slice.currentPage(),
                slice.pageSize(),
                slice.hasNext(),
                slice.feedbacks().stream().map(FeedbackSummary::from).toList()
        );
    }

    public record FeedbackSummary(
            UUID feedbackId,
            String content,
            Instant createdAt
    ) {

        private static FeedbackSummary from(Feedback feedback) {
            return new FeedbackSummary(
                    feedback.getId(),
                    feedback.getContent(),
                    feedback.getCreatedAt()
            );
        }
    }
}
