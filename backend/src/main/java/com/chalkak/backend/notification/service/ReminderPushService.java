package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.domain.PushEventIdPolicy;
import com.chalkak.backend.notification.domain.Reminder;
import com.chalkak.backend.notification.domain.ReminderTarget;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.notification.repository.ReminderRepository;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.repository.TopicRepository;
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
public class ReminderPushService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ReminderRepository reminderRepository;
    private final TopicRepository topicRepository;
    private final NotificationRepository notificationRepository;
    private final DirectPushPublisher directPushPublisher;
    private final Clock clock;
    private final TransactionTemplate readOnlyTransactionTemplate;
    private final TransactionTemplate transactionTemplate;
    // 단일 리마인더 전용 스레드에서 사용한다. 재시작하면 사라져 중복 발행이 가능하다.
    private final Map<UUID, Instant> attemptedReminders = new HashMap<>();

    public ReminderPushService(
            ReminderRepository reminderRepository,
            TopicRepository topicRepository,
            NotificationRepository notificationRepository,
            DirectPushPublisher directPushPublisher,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.reminderRepository = reminderRepository;
        this.topicRepository = topicRepository;
        this.notificationRepository = notificationRepository;
        this.directPushPublisher = directPushPublisher;
        this.clock = clock;
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void publishDueReminders() {
        List<Reminder> reminders = readOnlyTransactionTemplate
                .execute(status -> reminderRepository.findActiveReminders());
        Set<UUID> activeIds = new HashSet<>();
        for (Reminder reminder : reminders) {
            activeIds.add(reminder.getId());
            publishReminder(reminder);
        }
        attemptedReminders.keySet().retainAll(activeIds);
    }

    private void publishReminder(Reminder reminder) {
        Instant occurredAt = reminder.findDueOccurrence(clock.instant());
        if (occurredAt == null || occurredAt.equals(attemptedReminders.get(reminder.getId()))) {
            return;
        }
        UUID eventId = PushEventIdPolicy.createForOccurrence(
                NotificationType.REMINDER,
                reminder.getId(),
                occurredAt
        );
        try {
            PublicationTargets targets = transactionTemplate
                    .execute(status -> findTargets(reminder.getId(), occurredAt));
            if (targets.skipReason() != null) {
                log.atInfo().addKeyValue("type", "notification")
                        .addKeyValue("stage", "reminder_execution_skipped")
                        .addKeyValue("eventId", eventId).addKeyValue("reminderId", reminder.getId())
                        .addKeyValue("occurredAt", occurredAt)
                        .addKeyValue("reason", targets.skipReason())
                        .log("리마인더 실행 생략");
                return;
            }
            publishTargets(
                    reminder.getId(),
                    eventId,
                    occurredAt,
                    targets.userIds()
            );
        } catch (RuntimeException exception) {
            log.atError().addKeyValue("type", "notification")
                    .addKeyValue("stage", "reminder_execution_failed")
                    .addKeyValue("eventId", eventId).addKeyValue("reminderId", reminder.getId())
                    .addKeyValue("occurredAt", occurredAt)
                    .addKeyValue("errorCode", exception.getClass().getSimpleName())
                    .log("리마인더 실행 실패");
        }
    }

    // 리마인더 행을 잠근 채 다시 확인한다. 수정·삭제 중이거나 일정이 바뀌었으면 기록하지 않고
    // 다음 확인 때 새 내용으로 판단한다.
    private PublicationTargets findTargets(UUID reminderId, Instant occurredAt) {
        Reminder current = reminderRepository.findActiveForPublication(reminderId).orElse(null);
        if (current == null) {
            return PublicationTargets.skipped("REMINDER_LOCKED_OR_DELETED");
        }
        if (!occurredAt.equals(current.findDueOccurrence(clock.instant()))) {
            return PublicationTargets.skipped("SCHEDULE_CHANGED");
        }
        // 발행 도중 중단되거나 결과가 불명이어도 같은 프로세스에서 다시 발행하지 않는다.
        attemptedReminders.put(reminderId, occurredAt);
        if (current.getTarget() == ReminderTarget.ALL) {
            return PublicationTargets.of(notificationRepository.findTopicPushEnabledUserIds());
        }
        return findTopicTargets(current.getTarget(), occurredAt);
    }

    // 오늘 주제는 회차의 KST 날짜로 찾는다. 참여 여부는 오늘 주제가 있어야 판단할 수 있으므로 주제가 없으면
    // 보내지 않는다. 리마인더는 독려만이 아니므로 주제의 참여 가능 시간과 관계없이 보낸다.
    private PublicationTargets findTopicTargets(ReminderTarget target, Instant occurredAt) {
        Topic topic = topicRepository.findActiveByTopicDate(LocalDate.ofInstant(occurredAt, KST))
                .orElse(null);
        if (topic == null) {
            return PublicationTargets.skipped("NO_TOPIC");
        }
        if (target == ReminderTarget.TOPIC_PARTICIPANTS) {
            return PublicationTargets.of(
                    notificationRepository.findTopicPushEnabledParticipantIds(topic.getId()));
        }
        return PublicationTargets.of(
                notificationRepository.findTopicPushEnabledNonParticipantIds(topic.getId()));
    }

    private void publishTargets(
            UUID reminderId,
            UUID eventId,
            Instant occurredAt,
            List<UUID> userIds
    ) {
        DirectPublicationSummary summary = directPushPublisher.publish(
                new DirectPushOccurrence(
                        eventId,
                        NotificationType.REMINDER,
                        NotificationSourceType.REMINDER,
                        reminderId,
                        occurredAt,
                        "reminder"
                ),
                userIds
        );
        log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "reminder_publication_summary")
                .addKeyValue("eventId", eventId).addKeyValue("reminderId", reminderId)
                .addKeyValue("occurredAt", occurredAt)
                .addKeyValue("expiresAt", summary.expiresAt())
                .addKeyValue("targetCount", summary.targetCount())
                .addKeyValue("acceptedCount", summary.acceptedCount())
                .addKeyValue("failedCount", summary.failedCount())
                .addKeyValue("unknownCount", summary.unknownCount())
                .addKeyValue("expiredCount", summary.expiredCount())
                .log("리마인더 SQS 발행 집계");
    }

    private record PublicationTargets(List<UUID> userIds, String skipReason) {

        private static PublicationTargets of(List<UUID> userIds) {
            return new PublicationTargets(userIds, null);
        }

        private static PublicationTargets skipped(String reason) {
            return new PublicationTargets(List.of(), reason);
        }
    }
}
