package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.domain.Reminder;
import com.chalkak.backend.notification.domain.ReminderTarget;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.notification.repository.ReminderRepository;
import java.time.Clock;
import java.time.Instant;
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

    private final ReminderRepository reminderRepository;
    private final NotificationRepository notificationRepository;
    private final DirectPushPublisher directPushPublisher;
    private final Clock clock;
    private final TransactionTemplate readOnlyTransactionTemplate;
    private final TransactionTemplate transactionTemplate;
    // 단일 리마인더 전용 스레드에서 사용한다. 재시작하면 사라져 중복 발행이 가능하다.
    private final Map<UUID, Instant> attemptedReminders = new HashMap<>();

    public ReminderPushService(
            ReminderRepository reminderRepository,
            NotificationRepository notificationRepository,
            DirectPushPublisher directPushPublisher,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.reminderRepository = reminderRepository;
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
        UUID eventId = UUID.randomUUID();
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
        if (current.getTarget() != ReminderTarget.ALL) {
            return PublicationTargets.skipped("TARGET_NOT_SUPPORTED");
        }
        return PublicationTargets.of(notificationRepository.findTopicPushEnabledUserIds());
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
