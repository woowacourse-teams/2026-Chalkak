package com.chalkak.backend.feedback.api.v1.dto.response;

import com.chalkak.backend.feedback.service.FeedbackSubmissionResult;
import java.time.Instant;
import java.util.UUID;

public record FeedbackSubmissionResponse(
        UUID feedbackId,
        Instant createdAt
) {

    public static FeedbackSubmissionResponse from(FeedbackSubmissionResult result) {
        return new FeedbackSubmissionResponse(result.feedbackId(), result.createdAt());
    }
}
