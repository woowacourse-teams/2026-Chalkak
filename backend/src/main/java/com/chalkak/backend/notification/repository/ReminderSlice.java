package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.Reminder;
import java.util.List;

public record ReminderSlice(List<Reminder> reminders, boolean hasNext) {
}
