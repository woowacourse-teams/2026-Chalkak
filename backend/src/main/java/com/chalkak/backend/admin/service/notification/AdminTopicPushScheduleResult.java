package com.chalkak.backend.admin.service.notification;

import com.chalkak.backend.notification.domain.TopicPushSchedule;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record AdminTopicPushScheduleResult(
        UUID scheduleId,
        LocalTime sendTime,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminTopicPushScheduleResult from(TopicPushSchedule schedule) {
        return new AdminTopicPushScheduleResult(schedule.getId(), schedule.getSendTime(),
                schedule.getCreatedAt(), schedule.getUpdatedAt());
    }
}
