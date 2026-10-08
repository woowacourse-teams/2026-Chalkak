package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.Notification;
import com.chalkak.backend.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class NotificationRelayService {

    private static final int BATCH_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final PushMessagePublisher pushMessagePublisher;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public NotificationRelayService(
            NotificationRepository notificationRepository,
            PushMessagePublisher pushMessagePublisher,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.notificationRepository = notificationRepository;
        this.pushMessagePublisher = pushMessagePublisher;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate
                .setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void publishPendingNotifications() {
        for (UUID notificationId : notificationRepository.findDuePublicationIds(clock.instant(),
                BATCH_SIZE)) {
            try {
                Boolean processed = transactionTemplate.execute(status -> publish(notificationId));
                if (Boolean.TRUE.equals(processed)) {
                    log.atInfo().addKeyValue("type", "notification")
                            .addKeyValue("stage", "relay_transaction_committed")
                            .addKeyValue("notificationId", notificationId)
                            .log("알림 발행 처리 트랜잭션 완료");
                }
            } catch (RuntimeException exception) {
                // SQS 수락 뒤 DB 반영 실패도 여기에 포함된다. 원문/스택에 메시지·자격증명을 남기지 않는다.
                log.atError().addKeyValue("type", "notification")
                        .addKeyValue("stage", "relay_transaction_failed")
                        .addKeyValue("notificationId", notificationId)
                        .addKeyValue("errorCode", exception.getClass().getSimpleName())
                        .log("알림 발행 처리 트랜잭션 실패");
            }
        }
    }

    private boolean publish(UUID notificationId) {
        Instant now = clock.instant();
        Notification notification = notificationRepository
                .findPendingPublicationForUpdate(notificationId, now)
                .orElse(null);
        if (notification == null) {
            return false;
        }
        if (notification.isPushExpired(now)) {
            notification.expireSqsPublication();
            logResult(notification, "EXPIRED", null, null);
            return true;
        }
        PushMessage message = createPushMessage(notification);
        logResult(notification, "REQUESTED", null, null);
        PushPublicationResult result = pushMessagePublisher.publish(message);
        applyPublicationResult(notification, result);
        return true;
    }

    private PushMessage createPushMessage(Notification notification) {
        return new PushMessage(
                notification.getEventKey(), notification.getId(), notification.getUserId(),
                notification.getType(), notification.getSourceType(), notification.getSourceId(),
                notification.getCreatedAt(), notification.getPushExpiresAt());
    }

    private void applyPublicationResult(
            Notification notification,
            PushPublicationResult result
    ) {
        Instant attemptedAt = clock.instant();
        if (result.isAccepted()) {
            notification.markSqsPublished(attemptedAt);
            logResult(notification, "SQS_ACCEPTED", result.messageId(), null);
            return;
        }
        if (result.retryable()) {
            notification.retrySqsPublication(attemptedAt);
            logResult(notification, notification.getSqsPublishStatus().name(), null,
                    result.errorCode());
            return;
        }
        notification.failSqsPublication();
        logResult(notification, "FAILED", null, result.errorCode());
    }

    private void logResult(
            Notification notification,
            String result,
            String messageId,
            String errorCode
    ) {
        var event = log.atInfo();
        if (errorCode != null) {
            event = log.atWarn();
        }
        event.addKeyValue("type", "notification")
                .addKeyValue("stage", "relay")
                .addKeyValue("eventId", notification.getEventKey())
                .addKeyValue("notificationId", notification.getId())
                .addKeyValue("sqsMessageId", messageId)
                .addKeyValue("result", result)
                .addKeyValue("errorCode", errorCode)
                .addKeyValue("nextAttemptAt", notification.getNextAttemptAt())
                .log("알림 SQS 발행 처리");
    }
}
