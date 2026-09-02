package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.NotificationDispatch;
import com.chalkak.backend.notification.repository.NotificationOutboxRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class NotificationOutboxStatusService {

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final Clock clock;

    public NotificationOutboxStatusService(
            NotificationOutboxRepository notificationOutboxRepository,
            Clock clock
    ) {
        this.notificationOutboxRepository = notificationOutboxRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markSent(NotificationDispatch dispatch) {
        return notificationOutboxRepository.markSent(
                dispatch.outboxId(),
                dispatch.processingToken(),
                dispatch.attemptCount(),
                Instant.now(clock)
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markRetry(
            NotificationDispatch dispatch,
            Instant nextAttemptAt,
            String failureCode
    ) {
        return notificationOutboxRepository.markRetry(
                dispatch.outboxId(),
                dispatch.processingToken(),
                dispatch.attemptCount(),
                nextAttemptAt,
                failureCode,
                Instant.now(clock)
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailed(NotificationDispatch dispatch, String failureCode) {
        return notificationOutboxRepository.markFailed(
                dispatch.outboxId(),
                dispatch.processingToken(),
                dispatch.attemptCount(),
                failureCode,
                Instant.now(clock)
        );
    }
}
