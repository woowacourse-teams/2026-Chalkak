package com.chalkak.backend.admin.service.post;

import com.chalkak.backend.post.domain.ModerationStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminPostModerationResult(
        UUID postId,
        ModerationStatus moderationStatus,
        UUID moderatedBy,
        Instant moderatedAt,
        String rejectionReason) {
}
