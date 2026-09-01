package com.chalkak.backend.post.service;

import com.chalkak.backend.post.domain.ModerationStatus;
import java.util.UUID;

public record PostUpdateResult(
        UUID postId,
        String title,
        ModerationStatus moderationStatus
) {
}
