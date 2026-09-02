package com.chalkak.backend.admin.repository;

import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.PostImageUploadStatus;
import com.chalkak.backend.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record AdminPostDetailProjection(
        UUID postId,
        String title,
        ModerationStatus moderationStatus,
        Instant createdAt,
        Instant updatedAt,
        Instant moderatedAt,
        UUID moderatedBy,
        String rejectionReason,
        Instant deletedAt,
        UUID authorId,
        String authorEmail,
        UserStatus authorStatus,
        Instant authorDeletedAt,
        UUID topicId,
        String topicTitle,
        LocalDate topicDate,
        Instant topicStartsAt,
        Instant topicEndsAt,
        Instant topicDeletedAt,
        UUID photoId,
        String originalStorageKey,
        String thumbnailStorageKey,
        Map<String, Object> photoMetadata,
        Instant photoCreatedAt,
        Instant photoUpdatedAt,
        Instant photoDeletedAt,
        UUID uploadId,
        PostImageUploadStatus uploadStatus,
        String uploadRejectionReason,
        Instant uploadCreatedAt,
        Instant uploadUpdatedAt,
        long likeCount
) {
}
