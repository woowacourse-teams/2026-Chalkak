package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.notification.domain.NotificationDispatch;
import com.chalkak.backend.notification.domain.NotificationOutbox;
import com.chalkak.backend.notification.repository.NotificationOutboxRepository;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class NotificationOutboxClaimServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("도래한 알림을 짧은 트랜잭션에서 선점하고 불변 작업 정보로 반환한다")
    void claimNext_dueOutbox_claimsAndReturnsDispatch() {
        // Given
        UUID postId = UUID.randomUUID();
        NotificationOutbox outbox = NotificationOutbox.createPostModerationPending(
                postId,
                NOW.minusSeconds(1)
        );
        FakeNotificationOutboxRepository repository =
                new FakeNotificationOutboxRepository(List.of(outbox));
        NotificationOutboxClaimService service =
                new NotificationOutboxClaimService(repository, CLOCK);

        // When
        Optional<NotificationDispatch> dispatch = service.claimNext();

        // Then
        assertThat(dispatch).isPresent();
        assertThat(dispatch.orElseThrow().postId()).isEqualTo(postId);
        assertThat(dispatch.orElseThrow().attemptCount()).isEqualTo(1);
        assertThat(dispatch.orElseThrow().processingToken()).isNotNull();
        assertThat(outbox.getLeaseExpiresAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(repository.requestedBatchSize).isEqualTo(1);
        assertThat(repository.requestedAt).isEqualTo(NOW);
    }

    @Test
    @DisplayName("선점은 독립 트랜잭션으로 커밋되도록 REQUIRES_NEW를 사용한다")
    void claimNext_transactionBoundary_requiresNew() throws NoSuchMethodException {
        // Given
        Method method = NotificationOutboxClaimService.class.getMethod("claimNext");

        // When
        Transactional transactional = method.getAnnotation(Transactional.class);

        // Then
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    @DisplayName("배포 환경에서 주입한 선점 임대 기간을 적용한다")
    void claimNext_configuredLease_appliesConfiguration() {
        // Given
        NotificationOutbox outbox = NotificationOutbox.createPostModerationPending(
                UUID.randomUUID(),
                NOW.minusSeconds(1)
        );
        FakeNotificationOutboxRepository repository =
                new FakeNotificationOutboxRepository(List.of(outbox));
        NotificationOutboxClaimService service = new NotificationOutboxClaimService(
                repository,
                CLOCK,
                Duration.ofSeconds(45)
        );

        // When
        service.claimNext();

        // Then
        assertThat(outbox.getLeaseExpiresAt()).isEqualTo(NOW.plusSeconds(45));
    }

    private static final class FakeNotificationOutboxRepository
            implements NotificationOutboxRepository {

        private final List<NotificationOutbox> claimable;
        private Instant requestedAt;
        private int requestedBatchSize;

        private FakeNotificationOutboxRepository(List<NotificationOutbox> claimable) {
            this.claimable = new ArrayList<>(claimable);
        }

        @Override
        public boolean createPostModerationPending(UUID postId, Instant createdAt) {
            return false;
        }

        @Override
        public List<NotificationOutbox> findClaimableForUpdate(Instant now, int batchSize) {
            requestedAt = now;
            requestedBatchSize = batchSize;
            return List.copyOf(claimable);
        }

        @Override
        public boolean markSent(
                UUID outboxId,
                UUID processingToken,
                int attemptCount,
                Instant completedAt
        ) {
            return false;
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
            return false;
        }

        @Override
        public boolean markFailed(
                UUID outboxId,
                UUID processingToken,
                int attemptCount,
                String failureCode,
                Instant failedAt
        ) {
            return false;
        }
    }
}
