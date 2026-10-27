package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.PushDelivery;
import com.chalkak.backend.exception.PushDeliveryRecordException;
import com.chalkak.backend.notification.repository.PushDeliveryRepository;
import java.time.Duration;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class PushDeliveryService {
    private static final Duration RETRY_DELAY = Duration.ofMinutes(1);

    private final PushDeliveryRepository repository;
    private final PushDeliveryLocks locks = new PushDeliveryLocks();
    private final TransactionTemplate preparationTransaction;

    public PushDeliveryService(
            PushDeliveryRepository repository,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.preparationTransaction = new TransactionTemplate(transactionManager);
        this.preparationTransaction.setTimeout(5);
    }

    // 외부 응답 대기까지 DB 연결을 보유하는 상위 트랜잭션에서 호출하지 않는다.
    @Transactional(propagation = Propagation.NEVER)
    public PushProcessingResult process(
            PushMessage message,
            Function<PushDeliveryAttempt, PushProcessingResult> action
    ) {
        if (!locks.tryAcquire(message.eventId(), message.userId())) {
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
            locks.release(message.eventId(), message.userId());
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
                existing,
                preparationTransaction
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
