package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.notification.domain.NotificationChannel;
import com.chalkak.backend.notification.domain.NotificationDispatch;
import com.chalkak.backend.notification.domain.NotificationOutbox;
import com.chalkak.backend.notification.domain.NotificationTarget;
import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.repository.NotificationOutboxRepository;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class NotificationOutboxStatusServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("이미 다른 작업자가 재선점한 알림은 오래된 완료 결과로 갱신하지 않는다")
    void markSent_staleDispatch_returnsFalseWithoutOverwrite() {
        // Given
        FakeNotificationOutboxRepository repository = new FakeNotificationOutboxRepository();
        repository.updateAccepted = false;
        NotificationOutboxStatusService service =
                new NotificationOutboxStatusService(repository, CLOCK);
        NotificationDispatch staleDispatch = createDispatch(2);

        // When
        boolean updated = service.markSent(staleDispatch);

        // Then
        assertThat(updated).isFalse();
        assertThat(repository.sentToken).isEqualTo(staleDispatch.processingToken());
        assertThat(repository.sentAttemptCount).isEqualTo(2);
        assertThat(repository.updatedAt).isEqualTo(NOW);
    }

    @Test
    @DisplayName("성공 재시도 영구 실패 상태 변경은 모두 독립 트랜잭션을 사용한다")
    void statusMethods_transactionBoundaries_requireNew() throws NoSuchMethodException {
        // Given
        Method markSent = NotificationOutboxStatusService.class.getMethod(
                "markSent",
                NotificationDispatch.class
        );
        Method markRetry = NotificationOutboxStatusService.class.getMethod(
                "markRetry",
                NotificationDispatch.class,
                Instant.class,
                String.class
        );
        Method markFailed = NotificationOutboxStatusService.class.getMethod(
                "markFailed",
                NotificationDispatch.class,
                String.class
        );

        // When & Then
        assertThat(markSent.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(markRetry.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(markFailed.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.REQUIRES_NEW);
    }

    private NotificationDispatch createDispatch(int attemptCount) {
        return new NotificationDispatch(
                UUID.randomUUID(),
                UUID.randomUUID(),
                NotificationType.POST_MODERATION_PENDING,
                NotificationChannel.SLACK,
                NotificationTarget.ADMIN_MODERATION_REVIEWERS,
                attemptCount,
                UUID.randomUUID(),
                NOW.minusSeconds(10)
        );
    }

    private static final class FakeNotificationOutboxRepository
            implements NotificationOutboxRepository {

        private boolean updateAccepted;
        private UUID sentToken;
        private int sentAttemptCount;
        private Instant updatedAt;

        @Override
        public boolean createPostModerationPending(UUID postId, Instant createdAt) {
            return false;
        }

        @Override
        public List<NotificationOutbox> findClaimableForUpdate(Instant now, int batchSize) {
            return List.of();
        }

        @Override
        public boolean markSent(
                UUID outboxId,
                UUID processingToken,
                int attemptCount,
                Instant completedAt
        ) {
            sentToken = processingToken;
            sentAttemptCount = attemptCount;
            updatedAt = completedAt;
            return updateAccepted;
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
            return updateAccepted;
        }

        @Override
        public boolean markFailed(
                UUID outboxId,
                UUID processingToken,
                int attemptCount,
                String failureCode,
                Instant failedAt
        ) {
            return updateAccepted;
        }
    }
}
