package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.PushDelivery;
import com.chalkak.backend.exception.PushLeaseLostException;
import com.chalkak.backend.notification.repository.PushDeliveryRepository;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PushDeliveryService {
    private static final Duration RETRY_DELAY = Duration.ofMinutes(1);

    private final PushDeliveryRepository repository;
    private final ScheduledExecutorService executor;

    public PushDeliveryService(
            PushDeliveryRepository repository,
            @Qualifier("notificationLeaseRenewalExecutor") ScheduledExecutorService executor
    ) {
        this.repository = repository;
        this.executor = executor;
    }

    public PushProcessingResult process(
            PushMessage message,
            Function<PushDeliveryLease, PushProcessingResult> action
    ) {
        PushDelivery existing = repository.findByEventIdAndUserId(
                message.eventId(), message.userId()).orElse(null);
        if (existing != null && existing.isCompleted()) {
            logSkipped(message, "ALREADY_COMPLETED");
            return PushProcessingResult.completed();
        }
        try (PushDeliveryLease lease = new PushDeliveryLease(
                message,
                repository,
                executor
        )) {
            if (existing != null && existing.hasAcceptedDevice()) {
                lease.ensureClaimed();
            }
            return lease.process(action);
        } catch (PushLeaseLostException exception) {
            log.atWarn().addKeyValue("type", "notification")
                    .addKeyValue("stage", "worker_delivery_lease_unavailable")
                    .addKeyValue("eventId", message.eventId())
                    .addKeyValue("userId", message.userId()).log("푸시 처리 권한 확인 불가로 재처리 대기");
            return new PushProcessingResult(true, RETRY_DELAY);
        }
    }

    private void logSkipped(PushMessage message, String reason) {
        log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_delivery_skipped")
                .addKeyValue("eventId", message.eventId())
                .addKeyValue("userId", message.userId()).addKeyValue("reason", reason)
                .log("기록된 푸시 발송 결과에 따라 재발송 생략");
    }

}
