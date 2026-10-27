package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.PushDeviceDeliveryStatus;

import com.chalkak.backend.notification.domain.PushDeliveryStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.domain.PushDelivery;
import com.chalkak.backend.notification.repository.PushDeliveryRepository;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> renewal;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbc).clean();
        jdbc.update("""
                INSERT INTO users(id,email,signature_original_storage_key)
                VALUES (?, 'lease-test@example.com', 'lease/signature')
                """, USER_ID);
        executor = mock(ScheduledExecutorService.class);
        renewal = mock(ScheduledFuture.class);
        doReturn(renewal).when(executor).scheduleAtFixedRate(
                any(Runnable.class),
                eq(30L),
                eq(30L),
                eq(TimeUnit.SECONDS)
        );
        service = new PushDeliveryService(repository, executor);
        message = new PushMessage(
                UUID.randomUUID(),
                null,
                USER_ID,
                NotificationType.TOPIC,
                NotificationSourceType.TOPIC,
                UUID.randomUUID(),
                NOW,
                NOW.plusSeconds(1800)
        );
    }

    @Test
    @DisplayName("30초 주기로 예약한 갱신은 DB 시간을 기준으로 Lease를 늘리고 종료 시 예약을 취소한다")
    void process_activeLease_renewsAndCancelsOnExit() {
        // When
        PushProcessingResult result = service.process(message, lease -> {
            lease.send(
                    DEVICE_ID,
                    CLOCK,
                    () -> {
                        jdbc.update(
                                "UPDATE push_deliveries SET lease_until=clock_timestamp()+INTERVAL '5 seconds'");
                        Instant before = current().getLeaseUntil();
                        renewalTask().run();
                        assertThat(current().getLeaseUntil()).isAfter(before.plusSeconds(50));
                        return DevicePushResult.retryable("UNAVAILABLE");
                    }
            );
            return new PushProcessingResult(true, Duration.ofSeconds(60));
        });

        // Then
        assertThat(result.retryable()).isTrue();
        assertThat(current().getStatus()).isEqualTo(PushDeliveryStatus.PENDING);
        assertThat(current().getLeaseToken()).isNull();
        verify(renewal).cancel(false);
    }

    @Test
    @DisplayName("갱신에 실패하면 추가 기기 발송을 시작하지 않고 SQS 재처리를 요청한다")
    void process_expiredLeaseAtRenewal_stopsStartingDevices() {
        // When
        PushProcessingResult result = service.process(message, lease -> {
            lease.send(
                    DEVICE_ID,
                    CLOCK,
                    () -> {
                        jdbc.update(
                                "UPDATE push_deliveries SET lease_until=clock_timestamp()-INTERVAL '1 second'");
                        renewalTask().run();
                        return DevicePushResult.accepted();
                    }
            );
            lease.send(
                    UUID.randomUUID(),
                    CLOCK,
                    DevicePushResult::accepted
            );
            return PushProcessingResult.completed();
        });

        // Then
        assertThat(result.retryable()).isTrue();
        assertThat(current().getDeviceResults()).hasSize(1);
        assertThat(current().isCompleted()).isFalse();
        verify(renewal).cancel(false);
    }

    @Test
    @DisplayName("외부 발송 중 예상하지 못한 예외는 결과 불명으로 저장하고 Lease를 반납한다")
    void process_sendFails_recordsUnknownAndReleasesLease() {
        // When & Then
        PushProcessingResult result = service.process(message, lease -> {
            DevicePushResult failed = lease.send(
                    DEVICE_ID,
                    CLOCK,
                    () -> {
                        throw new IllegalStateException("send failed");
                    }
            );
            return new PushProcessingResult(failed.isRetryable(), failed.retryAfter());
        });
        assertThat(result.retryable()).isTrue();
        assertThat(current().getStatus()).isEqualTo(PushDeliveryStatus.PENDING);
        assertThat(current().getDeviceResults().get(DEVICE_ID).getStatus())
                .isEqualTo(PushDeviceDeliveryStatus.UNKNOWN);
        assertThat(current().getDeviceResults().get(DEVICE_ID).getLastErrorCode())
                .isEqualTo("IllegalStateException");
        verify(renewal).cancel(false);
    }

    @Test
    @DisplayName("수락한 기기가 있어도 다른 기기의 재처리가 필요하면 완료하지 않고 Lease를 반납한다")
    void process_acceptedAndRetryableDevices_keepsPendingDelivery() {
        // Given
        UUID other = UUID.randomUUID();

        // When
        PushProcessingResult result = service.process(message, lease -> {
            lease.send(
                    DEVICE_ID,
                    CLOCK,
                    DevicePushResult::accepted
            );
            lease.send(
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
        assertThat(current().getLeaseToken()).isNull();
        assertThat(current().getDeviceResults().get(DEVICE_ID).isAccepted()).isTrue();
        assertThat(current().getDeviceResults().get(other).getStatus())
                .isEqualTo(PushDeviceDeliveryStatus.RETRYABLE);
        verify(renewal).cancel(false);
    }

    @Test
    @DisplayName("시작 기록은 외부 호출 전에 커밋되고 결과 기록과 사용자 완료도 각각 커밋된다")
    void process_sendOutsideTransaction_preservesTransactionBoundaries() {
        // When
        PushProcessingResult result = service.process(message, lease -> {
            DevicePushResult sent = lease.send(
                    DEVICE_ID,
                    CLOCK,
                    () -> {
                        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                                .isFalse();
                        assertThat(current().getStatus()).isEqualTo(PushDeliveryStatus.PROCESSING);
                        assertThat(current().getDeviceResults().get(DEVICE_ID).getStatus())
                                .isEqualTo(PushDeviceDeliveryStatus.SENDING);
                        return DevicePushResult.accepted();
                    }
            );
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(current().getStatus()).isEqualTo(PushDeliveryStatus.PROCESSING);
            assertThat(current().getDeviceResults().get(DEVICE_ID).isAccepted()).isTrue();
            return new PushProcessingResult(sent.isRetryable(), sent.retryAfter());
        });

        // Then
        assertThat(result.retryable()).isFalse();
        assertThat(current().isCompleted()).isTrue();
        assertThat(current().getLeaseToken()).isNull();
    }

    @Test
    @DisplayName("외부 발송 대기 중 다른 스레드의 Lease 갱신은 차단되지 않는다")
    void process_waitingForSend_allowsConcurrentLeaseRenewal() {
        // Given
        try (var renewalThread = Executors.newSingleThreadExecutor()) {
            // When
            PushProcessingResult result = service.process(message, lease -> {
                DevicePushResult sent = lease.send(
                        DEVICE_ID,
                        CLOCK,
                        () -> {
                            jdbc.update("""
                                    UPDATE push_deliveries
                                    SET lease_until=clock_timestamp()+INTERVAL '5 seconds'
                                    """);
                            Instant before = current().getLeaseUntil();
                            CompletableFuture.runAsync(renewalTask(), renewalThread)
                                    .orTimeout(5, TimeUnit.SECONDS).join();
                            assertThat(current().getLeaseUntil()).isAfter(before.plusSeconds(50));
                            return DevicePushResult.accepted();
                        }
                );
                return new PushProcessingResult(sent.isRetryable(), sent.retryAfter());
            });

            // Then
            assertThat(result.retryable()).isFalse();
            assertThat(current().isCompleted()).isTrue();
            verify(renewal).cancel(false);
        }
    }

    @Test
    @DisplayName("기기 결과 저장 이후 후속 처리 예외는 수락 기록을 보존하고 Lease를 반납한다")
    void process_actionFailsAfterAcceptance_preservesResultAndReleasesLease() {
        // When & Then
        assertThatThrownBy(() -> service.process(message, lease -> {
            lease.send(
                    DEVICE_ID,
                    CLOCK,
                    DevicePushResult::accepted
            );
            throw new IllegalStateException("processing failed after result");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(current().getStatus()).isEqualTo(PushDeliveryStatus.PENDING);
        assertThat(current().getDeviceResults().get(DEVICE_ID).isAccepted()).isTrue();
        assertThat(current().getLeaseToken()).isNull();
        verify(renewal).cancel(false);
    }

    @Test
    @DisplayName("완료된 발송은 조건 조회와 갱신 예약 없이 종료한다")
    void process_completedDelivery_skipsActionAndRenewal() {
        // Given
        UUID token = UUID.randomUUID();
        PushDelivery delivery = repository.claim(
                message.eventId(),
                USER_ID,
                token,
                Duration.ofSeconds(60)
        ).orElseThrow();
        repository.complete(delivery.getId(), token);

        // When
        PushProcessingResult result = service.process(message, lease -> {
            throw new AssertionError("완료한 사건의 발송 조건을 다시 조회하면 안 된다");
        });

        // Then
        assertThat(result.retryable()).isFalse();
        verifyNoInteractions(executor);
    }

    private PushDelivery current() {
        return repository.findByEventIdAndUserId(message.eventId(), USER_ID).orElseThrow();
    }

    private Runnable renewalTask() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).scheduleAtFixedRate(
                task.capture(),
                eq(30L),
                eq(30L),
                eq(TimeUnit.SECONDS)
        );
        return task.getValue();
    }
}
