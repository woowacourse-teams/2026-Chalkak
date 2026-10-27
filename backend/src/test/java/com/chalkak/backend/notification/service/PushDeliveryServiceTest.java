package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.domain.PushDelivery;
import com.chalkak.backend.notification.domain.PushDeliveryStatus;
import com.chalkak.backend.notification.domain.PushDeviceDeliveryStatus;
import com.chalkak.backend.notification.repository.PushDeliveryRepository;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PushDeliveryServiceTest extends IntegrationTestSupport {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-26T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Autowired
    private PushDeliveryRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    private PushDeliveryService service;
    private PushMessage message;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbc).clean();
        insertUser(USER_ID, "delivery-test@example.com");
        service = new PushDeliveryService(repository);
        message = message(UUID.randomUUID(), USER_ID);
    }

    @Test
    @DisplayName("수락한 기기가 있어도 다른 기기의 재처리가 필요하면 미완료 이력을 유지한다")
    void process_acceptedAndRetryableDevices_keepsPendingDelivery() {
        // Given
        UUID other = UUID.randomUUID();

        // When
        PushProcessingResult result = service.process(message, attempt -> {
            attempt.send(
                    DEVICE_ID,
                    CLOCK,
                    DevicePushResult::accepted
            );
            attempt.send(
                    other,
                    CLOCK,
                    () -> DevicePushResult.retryable("UNAVAILABLE")
            );
            return new PushProcessingResult(true, Duration.ofSeconds(120));
        });

        // Then
        assertThat(result.retryable()).isTrue();
        assertThat(result.retryAfter()).isEqualTo(Duration.ofSeconds(120));
        assertThat(current().getStatus()).isEqualTo(PushDeliveryStatus.PENDING);
        assertThat(current().getDeviceResults().get(DEVICE_ID).isAccepted()).isTrue();
        assertThat(current().getDeviceResults().get(other).getStatus())
                .isEqualTo(PushDeviceDeliveryStatus.RETRYABLE);
    }

    @Test
    @DisplayName("SDK 예외는 결과 불명으로 저장하고 락을 해제해 다음 재처리를 허용한다")
    void process_sendFails_recordsUnknownAndUnlocks() {
        // When
        PushProcessingResult failed = service.process(message, attempt -> {
            DevicePushResult sent = attempt.send(
                    DEVICE_ID,
                    CLOCK,
                    () -> {
                        throw new IllegalStateException("send failed");
                    }
            );
            return new PushProcessingResult(sent.isRetryable(), sent.retryAfter());
        });

        // Then
        assertThat(failed.retryable()).isTrue();
        assertThat(current().getDeviceResults().get(DEVICE_ID).getStatus())
                .isEqualTo(PushDeviceDeliveryStatus.UNKNOWN);
        assertThat(current().getDeviceResults().get(DEVICE_ID).getLastErrorCode())
                .isEqualTo("IllegalStateException");

        // When & Then
        assertThat(send(message).retryable()).isFalse();
        assertThat(current().isCompleted()).isTrue();
        assertThat(current().getDeviceResults().get(DEVICE_ID).getAttemptCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("수락 결과 이후 예외가 나도 수락 기록은 남고 락은 해제된다")
    void process_actionFailsAfterAcceptance_preservesResultAndUnlocks() {
        // When & Then
        assertThatThrownBy(() -> service.process(message, attempt -> {
            attempt.send(
                    DEVICE_ID,
                    CLOCK,
                    DevicePushResult::accepted
            );
            throw new IllegalStateException("processing failed after result");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(current().getStatus()).isEqualTo(PushDeliveryStatus.PENDING);
        assertThat(current().getDeviceResults().get(DEVICE_ID).isAccepted()).isTrue();

        PushProcessingResult retried = service.process(message, attempt -> {
            DevicePushResult skipped = attempt.send(
                    DEVICE_ID,
                    CLOCK,
                    () -> {
                        throw new AssertionError("수락한 기기는 다시 발송하면 안 된다");
                    }
            );
            assertThat(skipped.status()).isEqualTo(DevicePushResult.Status.SKIPPED);
            return PushProcessingResult.completed();
        });
        assertThat(retried.retryable()).isFalse();
        assertThat(current().isCompleted()).isTrue();
    }

    @Test
    @DisplayName("시작 기록이 실패하면 발송 없이 종료하고 락 해제 후 같은 사건을 다시 처리한다")
    void process_startRecordFails_unlocksWithoutSending() {
        // Given
        jdbc.execute("""
                ALTER TABLE push_deliveries ADD CONSTRAINT ck_test_push_delivery_start
                CHECK (device_results = '{}'::jsonb)
                """);
        try {
            // When
            PushProcessingResult failed = service.process(message, attempt -> {
                attempt.send(
                        DEVICE_ID,
                        CLOCK,
                        () -> {
                            throw new AssertionError("시작 기록이 실패하면 FCM을 호출하면 안 된다");
                        }
                );
                return PushProcessingResult.completed();
            });

            // Then: 트랜잭션 통합은 보류하므로 먼저 생성한 PENDING 행은 남는다.
            assertThat(failed.retryable()).isTrue();
            assertThat(current().getStatus()).isEqualTo(PushDeliveryStatus.PENDING);
            assertThat(current().getDeviceResults()).isEmpty();
        } finally {
            jdbc.execute("ALTER TABLE push_deliveries DROP CONSTRAINT ck_test_push_delivery_start");
        }
        assertThat(send(message).retryable()).isFalse();
        assertThat(current().isCompleted()).isTrue();
    }

    @Test
    @DisplayName("시작 기록은 FCM 호출 전에 커밋되고 FCM 대기 중에는 DB 트랜잭션이 없다")
    void process_sendOutsideTransaction_preservesTransactionBoundaries() {
        // When
        PushProcessingResult result = service.process(message, attempt -> {
            DevicePushResult sent = attempt.send(
                    DEVICE_ID,
                    CLOCK,
                    () -> {
                        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                                .isFalse();
                        assertThat(current().getDeviceResults().get(DEVICE_ID).getStatus())
                                .isEqualTo(PushDeviceDeliveryStatus.SENDING);
                        return DevicePushResult.accepted();
                    }
            );
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(current().getStatus()).isEqualTo(PushDeliveryStatus.PENDING);
            assertThat(current().getDeviceResults().get(DEVICE_ID).isAccepted()).isTrue();
            return new PushProcessingResult(sent.isRetryable(), sent.retryAfter());
        });

        // Then
        assertThat(result.retryable()).isFalse();
        assertThat(current().isCompleted()).isTrue();
    }

    @Test
    @DisplayName("같은 사건·회원은 DB 행이 생기기 전에도 중복 진입을 막고 다음 수신으로 넘긴다")
    void process_sameKeyBeforeRecordExists_rejectsConcurrentAction() throws Exception {
        // Given
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var first = pool.submit(() -> service.process(message, attempt -> {
                entered.countDown();
                await(finish);
                attempt.send(
                        DEVICE_ID,
                        CLOCK,
                        DevicePushResult::accepted
                );
                return PushProcessingResult.completed();
            }));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(repository.findByEventIdAndUserId(message.eventId(), USER_ID)).isEmpty();

                // When
                PushProcessingResult duplicate = service.process(message, attempt -> {
                    throw new AssertionError("같은 키의 진행 중 작업에 진입하면 안 된다");
                });

                // Then
                assertThat(duplicate.retryable()).isTrue();
                assertThat(duplicate.retryAfter()).isEqualTo(Duration.ofMinutes(1));
            } finally {
                finish.countDown();
            }
            assertThat(first.get(5, TimeUnit.SECONDS).retryable()).isFalse();
        }
        assertThat(service.process(message, attempt -> {
            throw new AssertionError("완료 기록을 먼저 확인해야 한다");
        }).retryable()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("사건이나 회원 중 하나가 다르면 기존 작업이 FCM을 기다려도 병렬로 처리한다")
    void process_differentKey_runsWhileOtherIsSending(boolean differentUser) throws Exception {
        // Given
        UUID otherUser = USER_ID;
        UUID otherEvent = UUID.randomUUID();
        if (differentUser) {
            otherUser = UUID.randomUUID();
            otherEvent = message.eventId();
            insertUser(otherUser, "other-delivery@example.com");
        }
        PushMessage otherMessage = message(otherEvent, otherUser);
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> service.process(message, attempt -> {
                attempt.send(
                        DEVICE_ID,
                        CLOCK,
                        () -> {
                            sending.countDown();
                            await(finish);
                            return DevicePushResult.accepted();
                        }
                );
                return PushProcessingResult.completed();
            }));
            try {
                assertThat(sending.await(5, TimeUnit.SECONDS)).isTrue();

                // When & Then
                var other = pool.submit(() -> send(otherMessage));
                assertThat(other.get(5, TimeUnit.SECONDS).retryable()).isFalse();
                assertThat(first.isDone()).isFalse();
                assertThat(repository.findByEventIdAndUserId(otherMessage.eventId(),
                        otherMessage.userId()).orElseThrow().isCompleted()).isTrue();
            } finally {
                finish.countDown();
            }
            assertThat(first.get(5, TimeUnit.SECONDS).retryable()).isFalse();
        }
    }

    @Test
    @DisplayName("발송할 기기가 없으면 이력을 만들지 않고 같은 키의 다음 처리를 허용한다")
    void process_noDevice_createsNoRecordAndUnlocks() {
        // When & Then
        assertThat(
                service.process(message, attempt -> PushProcessingResult.completed()).retryable())
                .isFalse();
        assertThat(repository.findByEventIdAndUserId(message.eventId(), USER_ID)).isEmpty();
        assertThat(send(message).retryable()).isFalse();
    }

    @Test
    @DisplayName("완료된 이력은 기기 처리에 진입하지 않고 락을 해제한다")
    void process_completedDelivery_skipsActionAndUnlocks() {
        // Given
        PushDelivery delivery = repository.createIfAbsent(message.eventId(), USER_ID).orElseThrow();
        repository.complete(delivery.getId());

        // When & Then
        for (int index = 0; index < 2; index++) {
            assertThat(service.process(message, attempt -> {
                throw new AssertionError("완료된 사건은 다시 처리하면 안 된다");
            }).retryable()).isFalse();
        }
    }

    private PushProcessingResult send(PushMessage target) {
        return service.process(target, attempt -> {
            DevicePushResult result = attempt.send(
                    DEVICE_ID,
                    CLOCK,
                    DevicePushResult::accepted
            );
            return new PushProcessingResult(result.isRetryable(), result.retryAfter());
        });
    }

    private void insertUser(UUID userId, String email) {
        jdbc.update(
                """
                        INSERT INTO users(id,email,signature_original_storage_key)
                        VALUES (?, ?, ?)
                        """,
                userId,
                email,
                "delivery/" + userId
        );
    }

    private PushMessage message(UUID eventId, UUID userId) {
        return new PushMessage(
                eventId,
                null,
                userId,
                NotificationType.TOPIC,
                NotificationSourceType.TOPIC,
                UUID.randomUUID(),
                NOW,
                NOW.plusSeconds(1800)
        );
    }

    private PushDelivery current() {
        return repository.findByEventIdAndUserId(message.eventId(), USER_ID).orElseThrow();
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
