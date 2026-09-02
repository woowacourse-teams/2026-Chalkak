package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.NotificationOutbox;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationOutboxRepository {

    boolean createPostModerationPending(UUID postId, Instant createdAt);

    List<NotificationOutbox> findClaimableForUpdate(Instant now, int batchSize);

    boolean markSent(
            UUID outboxId,
            UUID processingToken,
            int attemptCount,
            Instant completedAt
    );

    boolean markRetry(
            UUID outboxId,
            UUID processingToken,
            int attemptCount,
            Instant nextAttemptAt,
            String failureCode,
            Instant failedAt
    );

    boolean markFailed(
            UUID outboxId,
            UUID processingToken,
            int attemptCount,
            String failureCode,
            Instant failedAt
    );
}
