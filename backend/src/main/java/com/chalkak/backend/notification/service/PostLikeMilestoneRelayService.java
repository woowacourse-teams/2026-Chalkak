package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.domain.PostLikePushMilestone;
import com.chalkak.backend.notification.repository.PostLikePushMilestoneRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class PostLikeMilestoneRelayService {

    private static final int BATCH_SIZE = 100;

    private final PostLikePushMilestoneRepository milestoneRepository;
    private final PushMessagePublisher pushMessagePublisher;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public PostLikeMilestoneRelayService(
            PostLikePushMilestoneRepository milestoneRepository,
            PushMessagePublisher pushMessagePublisher,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.milestoneRepository = milestoneRepository;
        this.pushMessagePublisher = pushMessagePublisher;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate
                .setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void publishPendingMilestones() {
        for (UUID milestoneId : findDuePublicationIds()) {
            publishSafely(milestoneId);
        }
    }

    private List<UUID> findDuePublicationIds() {
        return milestoneRepository.findDuePublicationIds(
                clock.instant(),
                BATCH_SIZE
        );
    }

    private void publishSafely(UUID milestoneId) {
        try {
            Boolean processed = transactionTemplate.execute(status -> publish(milestoneId));
            logTransactionCommitted(
                    milestoneId,
                    processed
            );
        } catch (RuntimeException exception) {
            logTransactionFailed(
                    milestoneId,
                    exception
            );
        }
    }

    private boolean publish(UUID milestoneId) {
        Instant now = clock.instant();
        PostLikePushMilestone milestone = milestoneRepository
                .findPendingPublicationForUpdate(milestoneId, now)
                .orElse(null);
        if (milestone == null) {
            return false;
        }
        if (milestone.isPushExpired(now)) {
            milestone.expirePublication();
            logResult(
                    milestone,
                    "EXPIRED",
                    null,
                    null
            );
            return true;
        }
        PushMessage message = createPushMessage(milestone);
        logResult(
                milestone,
                "REQUESTED",
                null,
                null
        );
        PushPublicationResult result = pushMessagePublisher.publish(message);
        applyPublicationResult(milestone, result);
        return true;
    }

    private PushMessage createPushMessage(PostLikePushMilestone milestone) {
        return new PushMessage(
                milestone.getId(),
                null,
                milestone.getUserId(),
                NotificationType.POST_LIKE_MILESTONE,
                NotificationSourceType.POST,
                milestone.getPostId(),
                milestone.getCreatedAt(),
                milestone.getPushExpiresAt()
        );
    }

    private void applyPublicationResult(
            PostLikePushMilestone milestone,
            PushPublicationResult result
    ) {
        Instant attemptedAt = clock.instant();
        if (result.isAccepted()) {
            milestone.markPublished(attemptedAt);
            logResult(
                    milestone,
                    "SQS_ACCEPTED",
                    result.messageId(),
                    null
            );
            return;
        }
        if (result.retryable()) {
            retryPublication(
                    milestone,
                    result,
                    attemptedAt
            );
            return;
        }
        milestone.failPublication();
        logResult(
                milestone,
                "FAILED",
                null,
                result.errorCode()
        );
    }

    private void retryPublication(
            PostLikePushMilestone milestone,
            PushPublicationResult result,
            Instant attemptedAt
    ) {
        milestone.retryPublication(attemptedAt);
        String publicationResult = "SQS_FAILED";
        if (result.isAcceptanceUnknown()) {
            publicationResult = "SQS_UNKNOWN";
        }
        logResult(
                milestone,
                publicationResult,
                null,
                result.errorCode()
        );
    }

    private void logTransactionCommitted(
            UUID milestoneId,
            Boolean processed
    ) {
        if (!Boolean.TRUE.equals(processed)) {
            return;
        }
        log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "like_milestone_relay_transaction_committed")
                .addKeyValue("eventId", milestoneId)
                .log("좋아요 마일스톤 발행 처리 트랜잭션 완료");
    }

    private void logTransactionFailed(
            UUID milestoneId,
            RuntimeException exception
    ) {
        log.atError().addKeyValue("type", "notification")
                .addKeyValue("stage", "like_milestone_relay_transaction_failed")
                .addKeyValue("eventId", milestoneId)
                .addKeyValue("errorCode", exception.getClass().getSimpleName())
                .log("좋아요 마일스톤 발행 처리 트랜잭션 실패");
    }

    private void logResult(
            PostLikePushMilestone milestone,
            String result,
            String messageId,
            String errorCode
    ) {
        var event = log.atInfo();
        if (errorCode != null) {
            event = log.atWarn();
        }
        event.addKeyValue("type", "notification")
                .addKeyValue("stage", "like_milestone_relay")
                .addKeyValue("eventId", milestone.getId())
                .addKeyValue("userId", milestone.getUserId())
                .addKeyValue("notificationType", NotificationType.POST_LIKE_MILESTONE)
                .addKeyValue("sourceId", milestone.getPostId())
                .addKeyValue("milestoneCount", milestone.getMilestoneCount())
                .addKeyValue("occurredAt", milestone.getCreatedAt())
                .addKeyValue("expiresAt", milestone.getPushExpiresAt())
                .addKeyValue("publishStatus", milestone.getPublishStatus())
                .addKeyValue("sqsMessageId", messageId)
                .addKeyValue("result", result)
                .addKeyValue("errorCode", errorCode)
                .addKeyValue("nextAttemptAt", milestone.getNextAttemptAt())
                .log("좋아요 마일스톤 SQS 발행 처리");
    }
}
