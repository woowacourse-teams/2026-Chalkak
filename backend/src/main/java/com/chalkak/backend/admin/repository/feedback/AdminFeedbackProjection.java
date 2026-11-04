package com.chalkak.backend.admin.repository.feedback;

import com.chalkak.backend.user.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminFeedbackProjection(
        UUID feedbackId,
        String content,
        Instant createdAt,
        Instant deletedAt,
        UUID authorId,
        String authorEmail,
        UserStatus authorStatus,
        String authorAppVersion,
        Instant authorDeletedAt,
        String answerContent,
        Instant answeredAt
) {
}
