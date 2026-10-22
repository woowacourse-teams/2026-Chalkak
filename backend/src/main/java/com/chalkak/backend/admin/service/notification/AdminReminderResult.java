package com.chalkak.backend.admin.service.notification;

import com.chalkak.backend.notification.domain.Reminder;
import com.chalkak.backend.notification.domain.ReminderRepeat;
import com.chalkak.backend.notification.domain.ReminderTarget;
import java.time.Instant;
import java.util.UUID;

public record AdminReminderResult(
        UUID reminderId,
        String title,
        String body,
        ReminderRepeat repeat,
        ReminderTarget target,
        Instant scheduledAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static AdminReminderResult from(Reminder reminder) {
        return new AdminReminderResult(
                reminder.getId(),
                reminder.getTitle(),
                reminder.getBody(),
                reminder.getRepeat(),
                reminder.getTarget(),
                reminder.getScheduledAt(),
                reminder.getCreatedAt(),
                reminder.getUpdatedAt()
        );
    }
}
