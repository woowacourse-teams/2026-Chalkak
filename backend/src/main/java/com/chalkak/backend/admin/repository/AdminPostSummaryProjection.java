package com.chalkak.backend.admin.repository;

import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminPostSummaryProjection(
        UUID postId,
        String title,
        ModerationStatus moderationStatus,
        UUID topicId,
        String topicTitle,
        LocalDate topicDate,
        UUID authorId,
        String authorEmail,
        UserStatus authorStatus,
        Instant authorDeletedAt,
        UUID photoId,
        String originalStorageKey,
        String thumbnailStorageKey,
        long likeCount,
        Instant createdAt,
        Instant moderatedAt,
        Instant deletedAt
) {
}
