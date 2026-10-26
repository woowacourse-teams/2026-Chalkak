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
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class PushDeliveryServiceTest extends IntegrationTestSupport {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-26T00:00:00Z");

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
            lease.startDevice(DEVICE_ID, NOW);
            jdbc.update(
                    "UPDATE push_deliveries SET lease_until=clock_timestamp()+INTERVAL '5 seconds'");
            Instant before = current().getLeaseUntil();
            renewalTask().run();
            assertThat(current().getLeaseUntil()).isAfter(before.plusSeconds(50));
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
            lease.startDevice(DEVICE_ID, NOW);
            jdbc.update(
                    "UPDATE push_deliveries SET lease_until=clock_timestamp()-INTERVAL '1 second'");
            renewalTask().run();
            lease.startDevice(UUID.randomUUID(), NOW);
            return PushProcessingResult.completed();
        });

        // Then
        assertThat(result.retryable()).isTrue();
        assertThat(current().getDeviceResults()).hasSize(1);
        assertThat(current().isCompleted()).isFalse();
        verify(renewal).cancel(false);
    }

    @Test
    @DisplayName("처리 중 예외가 나도 시작 기록을 보존하고 Lease를 해제하며 예약을 취소한다")
    void process_actionFails_preservesStartedAttemptAndReleasesLease() {
        // When & Then
        assertThatThrownBy(() -> service.process(message, lease -> {
            lease.startDevice(DEVICE_ID, NOW);
            throw new IllegalStateException("processing failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(current().getStatus()).isEqualTo(PushDeliveryStatus.PENDING);
        assertThat(current().getDeviceResults().get(DEVICE_ID).getStatus())
                .isEqualTo(PushDeviceDeliveryStatus.SENDING);
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
