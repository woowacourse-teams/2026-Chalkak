package com.chalkak.backend.feedback.api.v1.dto.response;

import com.chalkak.backend.feedback.service.FeedbackDetailResult;
import java.time.Instant;
import java.util.UUID;

public record FeedbackDetailResponse(
        UUID feedbackId,
        String content,
        Instant createdAt
) {

    public static FeedbackDetailResponse from(FeedbackDetailResult result) {
        return new FeedbackDetailResponse(
                result.feedbackId(),
                result.content(),
                result.createdAt()
        );
    }
}
