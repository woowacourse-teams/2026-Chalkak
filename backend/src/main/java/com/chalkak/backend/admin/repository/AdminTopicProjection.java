package com.chalkak.backend.admin.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminTopicProjection(
        UUID topicId,
        String title,
        LocalDate topicDate,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt,
        Instant updatedAt,
        long totalPostCount,
        long validatingPostCount,
        long pendingPostCount,
        long approvedPostCount,
        long rejectedPostCount
) {
}
