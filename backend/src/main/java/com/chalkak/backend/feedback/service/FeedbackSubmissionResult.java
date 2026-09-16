package com.chalkak.backend.feedback.service;

import com.chalkak.backend.feedback.domain.Feedback;
import java.time.Instant;
import java.util.UUID;

public record FeedbackSubmissionResult(
        UUID feedbackId,
        Instant createdAt
) {

    public static FeedbackSubmissionResult from(Feedback feedback) {
        return new FeedbackSubmissionResult(feedback.getId(), feedback.getCreatedAt());
    }
}
