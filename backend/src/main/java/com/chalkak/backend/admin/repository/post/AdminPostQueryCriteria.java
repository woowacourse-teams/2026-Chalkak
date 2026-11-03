package com.chalkak.backend.admin.repository.post;

import com.chalkak.backend.post.domain.ModerationStatus;
import java.util.UUID;

public record AdminPostQueryCriteria(
        ModerationStatus status,
        UUID topicId,
        UUID userId,
        AdminPostSort sort
) {
}
