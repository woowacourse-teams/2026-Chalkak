package com.chalkak.backend.post.service;

import com.chalkak.backend.post.domain.ModerationStatus;
import java.util.UUID;

public record PostCreationResult(
        UUID postId,
        ModerationStatus moderationStatus
) {
}
