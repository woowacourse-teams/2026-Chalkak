package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.PushDelivery;
import com.chalkak.backend.exception.PushDeliveryRecordException;
import com.chalkak.backend.notification.repository.PushDeliveryRepository;
import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PushDeliveryService {
    private static final Duration RETRY_DELAY = Duration.ofMinutes(1);

    private final PushDeliveryRepository repository;
    private final PushDeliveryLocks locks = new PushDeliveryLocks();

    public PushDeliveryService(PushDeliveryRepository repository) {
        this.repository = repository;
    }

    public PushProcessingResult process(
            PushMessage message,
            Function<PushDeliveryAttempt, PushProcessingResult> action
    ) {
        ReentrantLock lock = locks.tryAcquire(message.eventId(), message.userId()).orElse(null);
        if (lock == null) {
            log.atInfo().addKeyValue("type", "notification")
                    .addKeyValue("stage", "worker_delivery_lock_busy")
                    .addKeyValue("eventId", message.eventId())
                    .addKeyValue("userId", message.userId()).log("동일한 푸시 작업이 진행 중이므로 재처리 대기");
            return new PushProcessingResult(true, RETRY_DELAY);
        }
        try {
            return processLocked(message, action);
        } catch (PushDeliveryRecordException exception) {
            log.atWarn().addKeyValue("type", "notification")
                    .addKeyValue("stage", "worker_delivery_record_unavailable")
                    .addKeyValue("eventId", message.eventId())
                    .addKeyValue("userId", message.userId()).log("푸시 기록 확인 불가로 재처리 대기");
            return new PushProcessingResult(true, RETRY_DELAY);
        } finally {
            locks.release(
                    message.eventId(),
                    message.userId(),
                    lock
            );
        }
    }

    private PushProcessingResult processLocked(
            PushMessage message,
            Function<PushDeliveryAttempt, PushProcessingResult> action
    ) {
        PushDelivery existing = repository.findByEventIdAndUserId(
                message.eventId(), message.userId()).orElse(null);
        if (existing != null && existing.isCompleted()) {
            logSkipped(message, "ALREADY_COMPLETED");
            return PushProcessingResult.completed();
        }
        PushDeliveryAttempt attempt = new PushDeliveryAttempt(
                message,
                repository,
                existing
        );
        return attempt.process(action);
    }

    private void logSkipped(PushMessage message, String reason) {
        log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_delivery_skipped")
                .addKeyValue("eventId", message.eventId())
                .addKeyValue("userId", message.userId()).addKeyValue("reason", reason)
                .log("기록된 푸시 발송 결과에 따라 재발송 생략");
    }

}
