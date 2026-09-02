package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.notification.domain.NotificationChannel;
import com.chalkak.backend.notification.domain.NotificationOutbox;
import com.chalkak.backend.notification.repository.NotificationOutboxRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class NotificationOutboxWorkerTest {

    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("알림 발송이 성공하면 Outbox를 SENT로 변경한다")
    void processDueBatch_senderSucceeds_marksSent() {
        // Given
        Fixture fixture = createFixture(NotificationSendResult.sent());

        // When
        int processedCount = fixture.worker.processDueBatch();

        // Then
        assertThat(processedCount).isEqualTo(1);
        assertThat(fixture.repository.sent).isTrue();
        assertThat(fixture.repository.retried).isFalse();
        assertThat(fixture.repository.failed).isFalse();
    }

    @Test
    @DisplayName("일시 실패하면 Retry-After와 백오프 중 긴 기간 뒤로 재시도한다")
    void processDueBatch_retryableFailure_marksRetryWithDelay() {
        // Given
        Fixture fixture = createFixture(NotificationSendResult.retryable(
                Duration.ofMinutes(2),
                "SLACK_RATE_LIMITED"
        ));

        // When
        fixture.worker.processDueBatch();

        // Then
        assertThat(fixture.repository.retried).isTrue();
        assertThat(fixture.repository.nextAttemptAt).isEqualTo(NOW.plus(Duration.ofMinutes(2)));
        assertThat(fixture.repository.failureCode).isEqualTo("SLACK_RATE_LIMITED");
    }

    @Test
    @DisplayName("영구 실패하면 재시도하지 않고 FAILED로 변경한다")
    void processDueBatch_permanentFailure_marksFailed() {
        // Given
        Fixture fixture = createFixture(NotificationSendResult.permanentFailure(
                "SLACK_REQUEST_REJECTED"
        ));

        // When
        fixture.worker.processDueBatch();

        // Then
        assertThat(fixture.repository.failed).isTrue();
        assertThat(fixture.repository.retried).isFalse();
        assertThat(fixture.repository.failureCode).isEqualTo("SLACK_REQUEST_REJECTED");
    }

    @Test
    @DisplayName("일시 실패여도 5번째 시도에서는 FAILED로 종료한다")
    void processDueBatch_retryableFailureAtMaxAttempt_marksFailed() {
        // Given
        NotificationOutbox outbox = createOutboxAtAttemptFour();
        Fixture fixture = createFixture(
                outbox,
                NotificationSendResult.retryable(null, "SLACK_UNAVAILABLE")
        );

        // When
        fixture.worker.processDueBatch();

        // Then
        assertThat(fixture.repository.failed).isTrue();
        assertThat(fixture.repository.retried).isFalse();
        assertThat(fixture.repository.attemptCount).isEqualTo(5);
    }

    @Test
    @DisplayName("만료 임대를 6번째 재선점하면 외부 발송 없이 FAILED로 종료한다")
    void processDueBatch_reclaimedAfterMaxAttempt_failsWithoutSending() {
        // Given
        NotificationOutbox outbox = createOutboxAtAttempt(5);
        Fixture fixture = createFixture(outbox, NotificationSendResult.sent());

        // When
        fixture.worker.processDueBatch();

        // Then
        assertThat(fixture.sender.sendCount).isZero();
        assertThat(fixture.repository.failed).isTrue();
        assertThat(fixture.repository.attemptCount).isEqualTo(6);
        assertThat(fixture.repository.failureCode)
                .isEqualTo("NOTIFICATION_MAX_ATTEMPTS_EXCEEDED");
    }

    @Test
    @DisplayName("발송기가 예외를 던져도 비밀값을 남기지 않고 안전한 코드로 재시도한다")
    void processDueBatch_senderThrowsRuntimeException_marksSafeRetry() {
        // Given
        Fixture fixture = createFixture(null);
        fixture.sender.runtimeException = new IllegalStateException(
                "https://hooks.slack.com/services/secret"
        );

        // When
        fixture.worker.processDueBatch();

        // Then
        assertThat(fixture.repository.retried).isTrue();
        assertThat(fixture.repository.failureCode).isEqualTo("NOTIFICATION_SENDER_ERROR");
        assertThat(fixture.repository.failureCode).doesNotContain("secret", "hooks.slack.com");
    }

    @Test
    @DisplayName("재선점된 Outbox에 대한 오래된 상태 변경이 거부되어도 작업자는 안전하게 종료한다")
    void processDueBatch_staleStatusUpdate_noOpsSafely() {
        // Given
        Fixture fixture = createFixture(NotificationSendResult.sent());
        fixture.repository.updateAccepted = false;

        // When
        int processedCount = fixture.worker.processDueBatch();

        // Then
        assertThat(processedCount).isEqualTo(1);
        assertThat(fixture.repository.sentUpdateAccepted).isFalse();
    }

    @Test
    @DisplayName("Slack 발송을 호출하는 워커 범위에는 DB 트랜잭션이 없다")
    void processDueBatch_duringSenderCall_hasNoDatabaseTransaction() throws NoSuchMethodException {
        // Given
        Fixture fixture = createFixture(NotificationSendResult.sent());

        // When
        fixture.worker.processDueBatch();

        // Then
        assertThat(fixture.sender.transactionActiveDuringSend).isFalse();
        assertThat(NotificationOutboxWorker.class.getAnnotation(Transactional.class)).isNull();
        assertThat(NotificationOutboxWorker.class.getMethod("processDueBatch")
                .getAnnotation(Transactional.class)).isNull();
    }

    @Test
    @DisplayName("각 Slack 발송 직전에 해당 Outbox 한 건만 선점한다")
    void processDueBatch_multipleOutboxes_claimsEachImmediatelyBeforeSend() {
        // Given
        FakeNotificationOutboxRepository repository = new FakeNotificationOutboxRepository(
                List.of(createPendingOutbox(), createPendingOutbox())
        );
        FakeNotificationSender sender = new FakeNotificationSender(NotificationSendResult.sent());
        sender.repository = repository;
        NotificationOutboxWorker worker = createWorker(repository, sender, 20);

        // When
        int processedCount = worker.processDueBatch();

        // Then
        assertThat(processedCount).isEqualTo(2);
        assertThat(sender.claimQueryCountsDuringSend).containsExactly(1, 2);
    }

    @Test
    @DisplayName("한 번의 워커 실행은 설정한 배치 크기까지만 처리한다")
    void processDueBatch_moreOutboxesThanBatch_stopsAtBatchBoundary() {
        // Given
        FakeNotificationOutboxRepository repository = new FakeNotificationOutboxRepository(
                List.of(createPendingOutbox(), createPendingOutbox(), createPendingOutbox())
        );
        FakeNotificationSender sender = new FakeNotificationSender(NotificationSendResult.sent());
        NotificationOutboxWorker worker = createWorker(repository, sender, 2);

        // When
        int processedCount = worker.processDueBatch();

        // Then
        assertThat(processedCount).isEqualTo(2);
        assertThat(sender.sendCount).isEqualTo(2);
        assertThat(repository.remainingCount()).isEqualTo(1);
    }

    private Fixture createFixture(NotificationSendResult result) {
        return createFixture(
                NotificationOutbox.createPostModerationPending(
                        UUID.randomUUID(),
                        NOW.minusSeconds(1)
                ),
                result
        );
    }

    private Fixture createFixture(
            NotificationOutbox outbox,
            NotificationSendResult result
    ) {
        FakeNotificationOutboxRepository repository =
                new FakeNotificationOutboxRepository(List.of(outbox));
        FakeNotificationSender sender = new FakeNotificationSender(result);
        NotificationOutboxWorker worker = createWorker(repository, sender, 20);
        return new Fixture(repository, sender, worker);
    }

    private NotificationOutboxWorker createWorker(
            FakeNotificationOutboxRepository repository,
            FakeNotificationSender sender,
            int batchSize
    ) {
        NotificationOutboxClaimService claimService =
                new NotificationOutboxClaimService(repository, CLOCK);
        NotificationOutboxStatusService statusService =
                new NotificationOutboxStatusService(repository, CLOCK);
        NotificationOutboxWorker worker = new NotificationOutboxWorker(
                claimService,
                statusService,
                new NotificationMessageFactory(URI.create("https://admin.example.com")),
                new NotificationDispatcher(List.of(sender)),
                new NotificationRetryPolicy(),
                CLOCK,
                batchSize
        );
        return worker;
    }

    private NotificationOutbox createPendingOutbox() {
        return NotificationOutbox.createPostModerationPending(
                UUID.randomUUID(),
                NOW.minusSeconds(1)
        );
    }

    private NotificationOutbox createOutboxAtAttemptFour() {
        return createOutboxAtAttempt(4);
    }

    private NotificationOutbox createOutboxAtAttempt(int attemptCount) {
        NotificationOutbox outbox = NotificationOutbox.createPostModerationPending(
                UUID.randomUUID(),
                NOW.minusSeconds(300)
        );
        for (int attempt = 0; attempt < attemptCount; attempt++) {
            outbox.claim(
                    UUID.randomUUID(),
                    NOW.minusSeconds(300L - attempt),
                    Duration.ofSeconds(1)
            );
        }
        return outbox;
    }

    private record Fixture(
            FakeNotificationOutboxRepository repository,
            FakeNotificationSender sender,
            NotificationOutboxWorker worker
    ) {
    }

    private static final class FakeNotificationSender implements NotificationSender {

        private final NotificationSendResult result;
        private RuntimeException runtimeException;
        private boolean transactionActiveDuringSend;
        private FakeNotificationOutboxRepository repository;
        private final ArrayList<Integer> claimQueryCountsDuringSend = new ArrayList<>();
        private int sendCount;

        private FakeNotificationSender(NotificationSendResult result) {
            this.result = result;
        }

        @Override
        public NotificationChannel supportedChannel() {
            return NotificationChannel.SLACK;
        }

        @Override
        public NotificationSendResult send(NotificationMessage message) {
            sendCount += 1;
            transactionActiveDuringSend =
                    TransactionSynchronizationManager.isActualTransactionActive();
            if (repository != null) {
                claimQueryCountsDuringSend.add(repository.claimQueryCount);
            }
            if (runtimeException != null) {
                throw runtimeException;
            }
            return result;
        }
    }

    private static final class FakeNotificationOutboxRepository
            implements NotificationOutboxRepository {

        private final ArrayDeque<NotificationOutbox> claimableOutboxes;
        private boolean updateAccepted = true;
        private boolean sent;
        private boolean sentUpdateAccepted;
        private boolean retried;
        private boolean failed;
        private int attemptCount;
        private Instant nextAttemptAt;
        private String failureCode;
        private int claimQueryCount;

        private FakeNotificationOutboxRepository(List<NotificationOutbox> outboxes) {
            this.claimableOutboxes = new ArrayDeque<>(outboxes);
        }

        @Override
        public boolean createPostModerationPending(UUID postId, Instant createdAt) {
            return false;
        }

        @Override
        public List<NotificationOutbox> findClaimableForUpdate(Instant now, int batchSize) {
            claimQueryCount += 1;
            if (claimableOutboxes.isEmpty()) {
                return List.of();
            }
            return List.of(claimableOutboxes.removeFirst());
        }

        private int remainingCount() {
            return claimableOutboxes.size();
        }

        @Override
        public boolean markSent(
                UUID outboxId,
                UUID processingToken,
                int currentAttemptCount,
                Instant completedAt
        ) {
            sent = true;
            attemptCount = currentAttemptCount;
            sentUpdateAccepted = updateAccepted;
            return updateAccepted;
        }

        @Override
        public boolean markRetry(
                UUID outboxId,
                UUID processingToken,
                int currentAttemptCount,
                Instant scheduledAt,
                String currentFailureCode,
                Instant failedAt
        ) {
            retried = true;
            attemptCount = currentAttemptCount;
            nextAttemptAt = scheduledAt;
            failureCode = currentFailureCode;
            return updateAccepted;
        }

        @Override
        public boolean markFailed(
                UUID outboxId,
                UUID processingToken,
                int currentAttemptCount,
                String currentFailureCode,
                Instant failedAt
        ) {
            failed = true;
            attemptCount = currentAttemptCount;
            failureCode = currentFailureCode;
            return updateAccepted;
        }
    }
}
