package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.domain.PushEventIdPolicy;
import com.chalkak.backend.notification.domain.TopicPushSchedule;
import com.chalkak.backend.notification.repository.TopicPushScheduleRepository;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.repository.TopicRepository;
import com.chalkak.backend.notification.repository.NotificationRepository;
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
    private final NotificationRepository notificationRepository;
    private final DirectPushPublisher directPushPublisher;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    // 단일 주제 전용 스레드에서 사용한다. 재시작하면 사라져 중복 발행이 가능하다.
    private final Map<UUID, Instant> attemptedSchedules = new HashMap<>();

    public TopicPushService(
            TopicPushScheduleRepository scheduleRepository,
            TopicRepository topicRepository,
            NotificationRepository notificationRepository,
            DirectPushPublisher directPushPublisher,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.scheduleRepository = scheduleRepository;
        this.topicRepository = topicRepository;
        this.notificationRepository = notificationRepository;
        this.directPushPublisher = directPushPublisher;
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
        UUID eventId = PushEventIdPolicy.createForOccurrence(
                NotificationType.TOPIC,
                schedule.getId(),
                scheduledAt
        );
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
            publishTargets(
                    schedule.getId(),
                    eventId,
                    scheduledAt,
                    targets
            );
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
        return new PublicationTargets(topic.getId(),
                notificationRepository.findTopicPushEnabledNonParticipantIds(topic.getId()));
    }

    private void publishTargets(
            UUID scheduleId,
            UUID eventId,
            Instant scheduledAt,
            PublicationTargets targets
    ) {
        DirectPublicationSummary summary = directPushPublisher.publish(
                new DirectPushOccurrence(
                        eventId,
                        NotificationType.TOPIC,
                        NotificationSourceType.TOPIC,
                        targets.topicId(),
                        scheduledAt,
                        "topic"
                ),
                targets.userIds()
        );
        log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "topic_publication_summary")
                .addKeyValue("eventId", eventId).addKeyValue("scheduleId", scheduleId)
                .addKeyValue("sourceId", targets.topicId()).addKeyValue("occurredAt", scheduledAt)
                .addKeyValue("expiresAt", summary.expiresAt())
                .addKeyValue("targetCount", summary.targetCount())
                .addKeyValue("acceptedCount", summary.acceptedCount())
                .addKeyValue("failedCount", summary.failedCount())
                .addKeyValue("unknownCount", summary.unknownCount())
                .addKeyValue("expiredCount", summary.expiredCount())
                .log("주제 푸시 SQS 발행 집계");
    }

    private record PublicationTargets(UUID topicId, List<UUID> userIds) {
    }
}
