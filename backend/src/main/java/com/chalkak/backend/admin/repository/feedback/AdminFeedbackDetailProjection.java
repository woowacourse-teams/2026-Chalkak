package com.chalkak.backend.admin.repository.feedback;

import java.time.Instant;

public record AdminFeedbackDetailProjection(
        AdminFeedbackProjection feedback,
        String answerContent,
        Instant answeredAt
) {
}
