package com.chalkak.backend.notification.service;

import com.chalkak.backend.exception.PushDeliveryRecordException;
import com.chalkak.backend.notification.domain.PushDelivery;
import com.chalkak.backend.notification.domain.PushDelivery.DeviceResult;
import com.chalkak.backend.notification.domain.PushDeviceDeliveryStatus;
import com.chalkak.backend.notification.repository.PushDeliveryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class PushDeliveryAttempt {
    private final PushDeliveryRepository repository;
    private final PushMessage message;
    private PushDelivery delivery;

    PushDeliveryAttempt(
            PushMessage message,
            PushDeliveryRepository repository,
            PushDelivery delivery
    ) {
        this.message = message;
        this.repository = repository;
        this.delivery = delivery;
    }

    PushProcessingResult process(Function<PushDeliveryAttempt, PushProcessingResult> action) {
        PushProcessingResult result = action.apply(this);
        if (delivery == null || result.retryable()) {
            return result;
        }
        if (!repository.complete(delivery.getId())) {
            throw new PushDeliveryRecordException();
        }
        return PushProcessingResult.completed();
    }

    public DevicePushResult send(
            UUID deviceId,
            Clock clock,
            Supplier<DevicePushResult> action
    ) {
        DeviceResult started = startDevice(deviceId, clock.instant());
        if (started == null) {
            return DevicePushResult.skipped("DELIVERY_ALREADY_PROCESSED");
        }
        DevicePushResult result;
        try {
            result = action.get();
        } catch (RuntimeException exception) {
            save(deviceId, started.finish(
                    PushDeviceDeliveryStatus.UNKNOWN,
                    exception.getClass().getSimpleName(),
                    clock.instant()
            ));
            return DevicePushResult.retryable(exception.getClass().getSimpleName());
        }
        save(deviceId, started.finish(
                toDeviceStatus(result),
                result.errorCode(),
                clock.instant()
        ));
        return result;
    }

    private DeviceResult startDevice(UUID deviceId, Instant now) {
        if (delivery == null) {
            try {
                delivery = repository.createIfAbsent(message.eventId(), message.userId())
                        .orElseThrow(PushDeliveryRecordException::new);
            } catch (RuntimeException exception) {
                logRecordFailure(exception);
                throw new PushDeliveryRecordException();
            }
        }
        if (delivery.isCompleted() || !delivery.canSendTo(deviceId)) {
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

    private void save(UUID deviceId, DeviceResult result) {
        try {
            if (repository.saveDeviceResult(
                    delivery.getId(),
                    deviceId,
                    result
            )) {
                delivery.recordDeviceResult(deviceId, result);
                return;
            }
        } catch (RuntimeException exception) {
            logRecordFailure(exception);
        }
        throw new PushDeliveryRecordException();
    }

    private void logRecordFailure(RuntimeException exception) {
        log.atWarn().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_delivery_record_failed")
                .addKeyValue("eventId", message.eventId())
                .addKeyValue("userId", message.userId())
                .addKeyValue("errorCode", exception.getClass().getSimpleName())
                .log("푸시 발송 기록 저장 실패");
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
}
