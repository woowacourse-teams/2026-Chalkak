package com.chalkak.backend.notification.service;

import java.time.Instant;

public record DirectPublicationSummary(
        Instant expiresAt,
        int targetCount,
        int acceptedCount,
        int failedCount,
        int unknownCount,
        int expiredCount
) {
}
