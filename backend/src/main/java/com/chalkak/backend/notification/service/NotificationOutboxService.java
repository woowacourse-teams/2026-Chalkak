package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.repository.NotificationOutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationOutboxService {

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final Clock clock;

    /**
     * 게시물의 PENDING 전환과 같은 트랜잭션에서만 적재한다. 알림 행 저장이 실패하면 게시물 상태도
     * 함께 롤백되어 "검수 대기인데 알림 사건이 없는" 상태를 만들지 않는다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean enqueuePostModerationPending(UUID postId) {
        return notificationOutboxRepository.createPostModerationPending(
                postId,
                Instant.now(clock)
        );
    }
}
