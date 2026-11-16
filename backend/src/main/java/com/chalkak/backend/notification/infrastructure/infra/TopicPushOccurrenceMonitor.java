package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.domain.PushDeadlinePolicy;
import com.chalkak.backend.notification.domain.PushEventIdPolicy;
import com.chalkak.backend.notification.domain.TopicPushSchedule;
import com.chalkak.backend.notification.repository.TopicPushScheduleRepository;
import com.chalkak.backend.topic.repository.TopicRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("dev | prod")
@ConditionalOnProperty(prefix = "chalkak.notification.sqs", name = "topic-push-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
class TopicPushOccurrenceMonitor {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    // 매분 실행과 CloudWatch 로그 수집에 여유를 주고, 기존 30분 발송 기한 안에 대응할 시간을 남긴다.
    private static final Duration RESULT_GRACE_PERIOD = Duration.ofMinutes(5);

    private final TopicPushScheduleRepository scheduleRepository;
    private final TopicRepository topicRepository;
    private final CloudWatchTopicPushResults results;
    private final Clock clock;

    // 발행 및 공용 작업과 분리하고, 조회 시간을 매번 다음 실행 간격에 더하지 않는다.
    @Scheduled(fixedRate = 60_000, scheduler = "notificationTopicMonitorTaskScheduler")
    public void logMissingOccurrences() {
        try {
            Instant now = clock.instant();
            List<Occurrence> expected = findExpectedOccurrences(now);
            long missingCount = countMissingOccurrences(expected, now);
            log.atInfo().addKeyValue("type", "notification")
                    .addKeyValue("stage", "topic_monitor_snapshot")
                    .addKeyValue("missingCount", missingCount)
                    .log("주제 푸시 회차 결과 관측");
        } catch (RuntimeException exception) {
            log.atWarn().addKeyValue("type", "notification")
                    .addKeyValue("stage", "topic_monitor_failed")
                    .addKeyValue("errorCode", exception.getClass().getSimpleName())
                    .log("주제 푸시 회차 결과 조회 실패");
        }
    }

    private List<Occurrence> findExpectedOccurrences(Instant now) {
        List<TopicPushSchedule> schedules = scheduleRepository.findAllByOrderBySendTimeAsc();
        LocalDate today = LocalDate.ofInstant(now, KST);
        List<Occurrence> expected = new ArrayList<>();
        addExpectedOccurrences(
                schedules,
                today.minusDays(1),
                now,
                expected
        );
        addExpectedOccurrences(
                schedules,
                today,
                now,
                expected
        );
        return expected;
    }

    private void addExpectedOccurrences(
            List<TopicPushSchedule> schedules,
            LocalDate date,
            Instant now,
            List<Occurrence> expected
    ) {
        List<Occurrence> due = new ArrayList<>();
        for (TopicPushSchedule schedule : schedules) {
            Instant scheduledAt = date.atTime(schedule.getSendTime()).atZone(KST).toInstant();
            if (schedule.findDueOccurrence(scheduledAt) == null
                    || now.isBefore(scheduledAt.plus(RESULT_GRACE_PERIOD))
                    || PushDeadlinePolicy
                            .isExpiredAt(PushDeadlinePolicy.calculateExpiresAt(scheduledAt), now)) {
                continue;
            }
            UUID eventId = PushEventIdPolicy.createForOccurrence(
                    NotificationType.TOPIC,
                    schedule.getId(),
                    scheduledAt
            );
            due.add(new Occurrence(
                    schedule.getId(),
                    eventId,
                    scheduledAt
            ));
        }
        if (!due.isEmpty() && topicRepository.findActiveByTopicDate(date).isPresent()) {
            expected.addAll(due);
        }
    }

    private long countMissingOccurrences(List<Occurrence> expected, Instant now) {
        if (expected.isEmpty()) {
            return 0;
        }
        Instant earliest = expected.getFirst().scheduledAt();
        Set<String> recordedEventIds = results.findRecordedEventIds(earliest, now);
        long missingCount = 0;
        for (Occurrence occurrence : expected) {
            if (recordedEventIds.contains(occurrence.eventId().toString())) {
                continue;
            }
            missingCount++;
            log.atInfo().addKeyValue("type", "notification")
                    .addKeyValue("stage", "topic_monitor_missing")
                    .addKeyValue("scheduleId", occurrence.scheduleId())
                    .addKeyValue("eventId", occurrence.eventId())
                    .addKeyValue("occurredAt", occurrence.scheduledAt())
                    .log("주제 푸시 회차 결과 누락 의심");
        }
        return missingCount;
    }

    private record Occurrence(
            UUID scheduleId,
            UUID eventId,
            Instant scheduledAt
    ) {
    }
}
