package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.NotificationOutbox;
import com.chalkak.backend.notification.repository.NotificationOutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationOutboxRepositoryImpl implements NotificationOutboxRepository {

    private final NotificationOutboxJpaRepository notificationOutboxJpaRepository;

    @Override
    public boolean createPostModerationPending(UUID postId, Instant createdAt) {
        return notificationOutboxJpaRepository.createPostModerationPending(postId, createdAt) == 1;
    }

    @Override
    public List<NotificationOutbox> findClaimableForUpdate(Instant now, int batchSize) {
        return notificationOutboxJpaRepository.findClaimableForUpdate(now, batchSize);
    }

    @Override
    public boolean markSent(
            UUID outboxId,
            UUID processingToken,
            int attemptCount,
            Instant completedAt
    ) {
        return notificationOutboxJpaRepository.markSent(
                outboxId,
                processingToken,
                attemptCount,
                completedAt
        ) == 1;
    }

    @Override
    public boolean markRetry(
            UUID outboxId,
            UUID processingToken,
            int attemptCount,
            Instant nextAttemptAt,
            String failureCode,
            Instant failedAt
    ) {
        return notificationOutboxJpaRepository.markRetry(
                outboxId,
                processingToken,
                attemptCount,
                nextAttemptAt,
                failureCode,
                failedAt
        ) == 1;
    }

    @Override
    public boolean markFailed(
            UUID outboxId,
            UUID processingToken,
            int attemptCount,
            String failureCode,
            Instant failedAt
    ) {
        return notificationOutboxJpaRepository.markFailed(
                outboxId,
                processingToken,
                attemptCount,
                failureCode,
                failedAt
        ) == 1;
    }
}
