package com.chalkak.backend.admin.repository;

import com.chalkak.backend.user.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminFeedbackSummaryProjection(
        UUID feedbackId,
        String content,
        Instant createdAt,
        UUID userId,
        String email,
        UserStatus userStatus,
        String appVersion,
        Instant userDeletedAt
) {
}
