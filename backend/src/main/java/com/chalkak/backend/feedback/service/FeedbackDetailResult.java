package com.chalkak.backend.feedback.service;

import com.chalkak.backend.feedback.domain.Feedback;
import java.time.Instant;
import java.util.UUID;

public record FeedbackDetailResult(
        UUID feedbackId,
        String content,
        Instant createdAt,
        String answerContent,
        Instant answeredAt
) {

    public static FeedbackDetailResult from(Feedback feedback) {
        return new FeedbackDetailResult(
                feedback.getId(),
                feedback.getContent(),
                feedback.getCreatedAt(),
                feedback.isAnswerDeleted() ? null : feedback.getAnswerContent(),
                feedback.isAnswerDeleted() ? null : feedback.getAnsweredAt()
        );
    }
}
