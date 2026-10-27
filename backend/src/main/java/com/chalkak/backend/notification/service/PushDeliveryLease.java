package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.PushDelivery;
import com.chalkak.backend.notification.domain.PushDelivery.DeviceResult;
import com.chalkak.backend.notification.domain.PushDeviceDeliveryStatus;
import com.chalkak.backend.exception.PushLeaseLostException;
import com.chalkak.backend.notification.repository.PushDeliveryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class PushDeliveryLease implements AutoCloseable {
    private static final Duration LEASE_LIFETIME = Duration.ofSeconds(60);
    private static final Duration RENEWAL_INTERVAL = Duration.ofSeconds(30);

    private final PushDeliveryRepository repository;
    private final ScheduledExecutorService executor;
    private final PushMessage message;
    private final UUID token = UUID.randomUUID();
    private PushDelivery delivery;
    private ScheduledFuture<?> renewal;
    private boolean owned = true;
    private boolean completed;

    PushDeliveryLease(
            PushMessage message,
            PushDeliveryRepository repository,
            ScheduledExecutorService executor
    ) {
        this.message = message;
        this.repository = repository;
        this.executor = executor;
    }

    synchronized boolean isClaimed() {
        return delivery != null;
    }

    synchronized boolean isCompleted() {
        return completed;
    }

    public synchronized DeviceResult startDevice(UUID deviceId, Instant now) {
        ensureClaimed();
        if (completed || !delivery.canSendTo(deviceId)) {
            return null;
        }
        DeviceResult previous = delivery.getDeviceResults().get(deviceId);
        if (previous != null && (previous.getStatus() == PushDeviceDeliveryStatus.SENDING
                || previous.getStatus() == PushDeviceDeliveryStatus.UNKNOWN)) {
            log.atWarn().addKeyValue("type", "notification")
                    .addKeyValue("stage", "worker_delivery_previous_unknown")
                    .addKeyValue("eventId", message.eventId())
                    .addKeyValue("userId", message.userId()).addKeyValue("deviceId", deviceId)
                    .log("이전 FCM 요청 결과 불명 기록을 재처리");
        }
        DeviceResult started = delivery.startDevice(deviceId, now);
        save(deviceId, started);
        return started;
    }

    public synchronized void recordResult(
            UUID deviceId,
            DeviceResult started,
            DevicePushResult result,
            Instant now
    ) {
        save(deviceId, started.finish(
                toDeviceStatus(result),
                result.errorCode(),
                now
        ));
    }

    public synchronized void recordUnknown(
            UUID deviceId,
            DeviceResult started,
            RuntimeException exception,
            Instant now
    ) {
        save(deviceId, started.finish(
                PushDeviceDeliveryStatus.UNKNOWN,
                exception.getClass().getSimpleName(),
                now
        ));
    }

    @Override
    public synchronized void close() {
        if (renewal != null) {
            renewal.cancel(false);
        }
        if (delivery != null && owned && !completed) {
            try {
                repository.release(delivery.getId(), token);
            } catch (RuntimeException exception) {
                logLeaseFailure(exception);
            }
        }
        owned = false;
    }

    synchronized void ensureClaimed() {
        if (!owned) {
            throw new PushLeaseLostException();
        }
        if (delivery != null) {
            return;
        }
        delivery = claimOrFind();
        if (delivery != null && delivery.isCompleted()) {
            completed = true;
            logSkipped("ALREADY_COMPLETED");
            return;
        }
        if (delivery == null || !token.equals(delivery.getLeaseToken())) {
            owned = false;
            throw new PushLeaseLostException();
        }
        try {
            renewal = executor.scheduleAtFixedRate(
                    this::renew,
                    RENEWAL_INTERVAL.toSeconds(),
                    RENEWAL_INTERVAL.toSeconds(),
                    TimeUnit.SECONDS
            );
        } catch (RuntimeException exception) {
            owned = false;
            logLeaseFailure(exception);
            throw new PushLeaseLostException();
        }
    }

    private PushDelivery claimOrFind() {
        try {
            return repository.claim(
                    message.eventId(),
                    message.userId(),
                    token,
                    LEASE_LIFETIME
            ).orElseGet(() -> repository.findByEventIdAndUserId(
                    message.eventId(), message.userId()).orElse(null));
        } catch (RuntimeException exception) {
            owned = false;
            logLeaseFailure(exception);
            throw new PushLeaseLostException();
        }
    }

    private synchronized void renew() {
        if (!owned || completed) {
            return;
        }
        try {
            owned = repository.renew(
                    delivery.getId(),
                    token,
                    LEASE_LIFETIME
            );
        } catch (RuntimeException exception) {
            owned = false;
            logLeaseFailure(exception);
        }
    }

    private void save(UUID deviceId, DeviceResult result) {
        if (!owned) {
            throw new PushLeaseLostException();
        }
        try {
            if (repository.saveDeviceResult(
                    delivery.getId(),
                    token,
                    deviceId,
                    result
            )) {
                return;
            }
        } catch (RuntimeException exception) {
            logLeaseFailure(exception);
        }
        owned = false;
        throw new PushLeaseLostException();
    }

    private PushDeviceDeliveryStatus toDeviceStatus(DevicePushResult result) {
        if (result.isAccepted()) {
            return PushDeviceDeliveryStatus.ACCEPTED;
        }
        if (result.isRetryable()) {
            return PushDeviceDeliveryStatus.RETRYABLE;
        }
        if (result.isInvalidToken()) {
            return PushDeviceDeliveryStatus.INVALID_TOKEN;
        }
        if (result.isPermanentFailure()) {
            return PushDeviceDeliveryStatus.PERMANENT_FAILURE;
        }
        return PushDeviceDeliveryStatus.SKIPPED;
    }

    synchronized void complete() {
        if (!owned || !repository.complete(delivery.getId(), token)) {
            owned = false;
            throw new PushLeaseLostException();
        }
        completed = true;
    }

    private void logSkipped(String reason) {
        log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_delivery_skipped")
                .addKeyValue("eventId", message.eventId())
                .addKeyValue("userId", message.userId()).addKeyValue("reason", reason)
                .log("기록된 푸시 발송 결과에 따라 재발송 생략");
    }

    private void logLeaseFailure(RuntimeException exception) {
        log.atWarn().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_delivery_lease_failed")
                .addKeyValue("eventId", message.eventId())
                .addKeyValue("userId", message.userId())
                .addKeyValue("errorCode", exception.getClass().getSimpleName())
                .log("푸시 처리 권한·기록 저장 실패");
    }
}
