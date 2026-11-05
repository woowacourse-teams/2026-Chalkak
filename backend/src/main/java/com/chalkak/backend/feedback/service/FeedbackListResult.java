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

    public static FeedbackListResult from(
            FeedbackSlice slice,
            int currentPage,
            int pageSize
    ) {
        return new FeedbackListResult(
                currentPage,
                pageSize,
                slice.hasNext(),
                slice.feedbacks().stream().map(FeedbackSummary::from).toList()
        );
    }

    public record FeedbackSummary(
            UUID feedbackId,
            String content,
            Instant createdAt,
            boolean hasAnswer
    ) {

        private static FeedbackSummary from(Feedback feedback) {
            return new FeedbackSummary(
                    feedback.getId(),
                    feedback.getContent(),
                    feedback.getCreatedAt(),
                    feedback.hasCurrentAnswer()
            );
        }
    }
}
