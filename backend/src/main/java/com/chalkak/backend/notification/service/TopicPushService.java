package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.domain.PushDeadlinePolicy;
import com.chalkak.backend.notification.domain.TopicPushSchedule;
import com.chalkak.backend.notification.repository.TopicPushScheduleRepository;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.repository.TopicRepository;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class TopicPushService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TopicPushScheduleRepository scheduleRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final PushMessagePublisher publisher;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    // 단일 주제 전용 스레드에서 사용한다. 재시작하면 사라져 중복 발행이 가능하다.
    private final Map<UUID, Instant> attemptedSchedules = new HashMap<>();

    public TopicPushService(
            TopicPushScheduleRepository scheduleRepository,
            TopicRepository topicRepository,
            UserRepository userRepository,
            PushMessagePublisher publisher,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.scheduleRepository = scheduleRepository;
        this.topicRepository = topicRepository;
        this.userRepository = userRepository;
        this.publisher = publisher;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setReadOnly(true);
    }

    public void publishDueTopics() {
        List<TopicPushSchedule> schedules = transactionTemplate
                .execute(status -> scheduleRepository.findAllByOrderBySendTimeAsc());
        Set<UUID> configuredIds = new HashSet<>();
        for (TopicPushSchedule schedule : schedules) {
            configuredIds.add(schedule.getId());
            publishSchedule(schedule);
        }
        attemptedSchedules.keySet().retainAll(configuredIds);
    }

    private void publishSchedule(TopicPushSchedule schedule) {
        Instant scheduledAt = schedule.findDueOccurrence(clock.instant());
        if (scheduledAt == null || scheduledAt.equals(attemptedSchedules.get(schedule.getId()))) {
            return;
        }
        // 발행 도중 중단되거나 결과가 불명이어도 같은 프로세스에서 다시 발행하지 않는다.
        attemptedSchedules.put(schedule.getId(), scheduledAt);
        UUID eventId = UUID.randomUUID();
        try {
            PublicationTargets targets = transactionTemplate
                    .execute(status -> findTargets(schedule.getId(), scheduledAt));
            if (targets == null) {
                log.atInfo().addKeyValue("type", "notification")
                        .addKeyValue("stage", "topic_execution_skipped")
                        .addKeyValue("eventId", eventId).addKeyValue("scheduleId", schedule.getId())
                        .addKeyValue("occurredAt", scheduledAt)
                        .addKeyValue("reason", "NO_TOPIC_OR_SCHEDULE_CHANGED")
                        .log("주제 푸시 실행 생략");
                return;
            }
            publishTargets(schedule.getId(), eventId, scheduledAt, targets);
        } catch (RuntimeException exception) {
            log.atError().addKeyValue("type", "notification")
                    .addKeyValue("stage", "topic_execution_failed")
                    .addKeyValue("eventId", eventId).addKeyValue("scheduleId", schedule.getId())
                    .addKeyValue("occurredAt", scheduledAt)
                    .addKeyValue("errorCode", exception.getClass().getSimpleName())
                    .log("주제 푸시 실행 실패");
        }
    }

    private PublicationTargets findTargets(UUID scheduleId, Instant scheduledAt) {
        TopicPushSchedule current = scheduleRepository.findById(scheduleId).orElse(null);
        if (current == null || !scheduledAt.equals(current.findDueOccurrence(clock.instant()))) {
            return null;
        }
        Topic topic = topicRepository.findActiveByTopicDate(LocalDate.ofInstant(scheduledAt, KST))
                .orElse(null);
        if (topic == null) {
            return null;
        }
        return new PublicationTargets(topic.getId(), userRepository.findTopicPushRecipientIds());
    }

    private void publishTargets(
            UUID scheduleId,
            UUID eventId,
            Instant scheduledAt,
            PublicationTargets targets
    ) {
        Instant expiresAt = PushDeadlinePolicy.calculateExpiresAt(scheduledAt);
        int accepted = 0;
        int failed = 0;
        int unknown = 0;
        int expired = 0;
        for (UUID userId : targets.userIds()) {
            if (PushDeadlinePolicy.isExpiredAt(expiresAt, clock.instant())) {
                expired = targets.userIds().size() - accepted - failed - unknown;
                break;
            }
            PushMessage message = new PushMessage(eventId, null, userId, NotificationType.TOPIC,
                    NotificationSourceType.TOPIC, targets.topicId(), scheduledAt, expiresAt);
            PushPublicationResult result = publishMessage(message);
            if (result.isAccepted()) {
                accepted++;
                continue;
            }
            if (isAcceptanceUnknown(result)) {
                unknown++;
                continue;
            }
            failed++;
        }
        log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "topic_publication_summary")
                .addKeyValue("eventId", eventId).addKeyValue("scheduleId", scheduleId)
                .addKeyValue("sourceId", targets.topicId()).addKeyValue("occurredAt", scheduledAt)
                .addKeyValue("expiresAt", expiresAt)
                .addKeyValue("targetCount", targets.userIds().size())
                .addKeyValue("acceptedCount", accepted).addKeyValue("failedCount", failed)
                .addKeyValue("unknownCount", unknown).addKeyValue("expiredCount", expired)
                .log("주제 푸시 SQS 발행 집계");
    }

    private PushPublicationResult publishMessage(PushMessage message) {
        try {
            PushPublicationResult result = publisher.publish(message);
            log.atInfo().addKeyValue("type", "notification")
                    .addKeyValue("stage", "topic_publication_result")
                    .addKeyValue("eventId", message.eventId())
                    .addKeyValue("userId", message.userId())
                    .addKeyValue("sourceId", message.sourceId())
                    .addKeyValue("occurredAt", message.occurredAt())
                    .addKeyValue("result", getPublicationStatus(result))
                    .addKeyValue("messageId", result.messageId())
                    .addKeyValue("errorCode", result.errorCode())
                    .log("주제 푸시 SQS 발행 결과");
            return result;
        } catch (RuntimeException exception) {
            log.atError().addKeyValue("type", "notification")
                    .addKeyValue("stage", "topic_publication_unknown")
                    .addKeyValue("eventId", message.eventId())
                    .addKeyValue("userId", message.userId())
                    .addKeyValue("errorCode", exception.getClass().getSimpleName())
                    .log("주제 푸시 SQS 발행 결과 확인 실패");
            return PushPublicationResult.retry("SQS_ACCEPTANCE_UNKNOWN");
        }
    }

    private String getPublicationStatus(PushPublicationResult result) {
        if (result.isAccepted()) {
            return "SQS_ACCEPTED";
        }
        if (isAcceptanceUnknown(result)) {
            return "UNKNOWN";
        }
        return "FAILED";
    }

    private boolean isAcceptanceUnknown(PushPublicationResult result) {
        return "SQS_ACCEPTANCE_UNKNOWN".equals(result.errorCode())
                || "SQS_CLIENT_ERROR".equals(result.errorCode());
    }

    private record PublicationTargets(UUID topicId, List<UUID> userIds) {
    }
}
