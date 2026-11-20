package com.chalkak.backend.jev.service;

import com.chalkak.backend.post.service.PostModerationPendingEvent;
import com.chalkak.backend.post.service.PostCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class JevPostReadyListener {

    private final JevRegistrationService registration;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void register(PostModerationPendingEvent event) {
        registration.registerExistingPending(event.postId());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    @Order(0)
    public void registerNew(PostCreatedEvent event) {
        registration.registerNew(event.postId());
    }
}
