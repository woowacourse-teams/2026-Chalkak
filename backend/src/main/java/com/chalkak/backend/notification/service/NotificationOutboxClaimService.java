package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.NotificationDispatch;
import com.chalkak.backend.notification.repository.NotificationOutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class NotificationOutboxClaimService {

    private static final Duration DEFAULT_LEASE = Duration.ofSeconds(30);

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final Clock clock;
    private final Duration lease;

    public NotificationOutboxClaimService(
            NotificationOutboxRepository notificationOutboxRepository,
            Clock clock
    ) {
        this(notificationOutboxRepository, clock, DEFAULT_LEASE);
    }

    public NotificationOutboxClaimService(
            NotificationOutboxRepository notificationOutboxRepository,
            Clock clock,
            Duration lease
    ) {
        validateConfiguration(lease);
        this.notificationOutboxRepository = notificationOutboxRepository;
        this.clock = clock;
        this.lease = lease;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<NotificationDispatch> claimNext() {
        Instant claimedAt = Instant.now(clock);
        return notificationOutboxRepository.findClaimableForUpdate(claimedAt, 1)
                .stream()
                .findFirst()
                .map(outbox -> outbox.claim(UUID.randomUUID(), claimedAt, lease));
    }

    private void validateConfiguration(Duration configuredLease) {
        if (configuredLease == null
                || configuredLease.isZero()
                || configuredLease.isNegative()) {
            throw new IllegalArgumentException("알림 선점 임대 기간은 0보다 길어야 합니다.");
        }
    }
}
