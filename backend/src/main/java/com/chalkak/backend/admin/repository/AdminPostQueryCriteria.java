package com.chalkak.backend.admin.repository;

import com.chalkak.backend.post.domain.ModerationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminPostQueryCriteria(
        ModerationStatus status,
        UUID topicId,
        LocalDate topicDate,
        UUID userId,
        Instant createdAtFrom,
        Instant createdAtTo,
        AdminPostQuerySort sort
) {
}
