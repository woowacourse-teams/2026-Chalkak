package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

class NotificationOutboxServiceTransactionTest extends IntegrationTestSupport {

    @Autowired
    private NotificationOutboxService notificationOutboxService;

    @Test
    @DisplayName("업무 트랜잭션 밖에서는 게시물 검수 알림 Outbox를 적재할 수 없다")
    void enqueuePostModerationPending_withoutTransaction_throwsException() {
        assertThatThrownBy(() -> notificationOutboxService.enqueuePostModerationPending(
                UUID.randomUUID()
        )).isInstanceOf(IllegalTransactionStateException.class);
    }
}
