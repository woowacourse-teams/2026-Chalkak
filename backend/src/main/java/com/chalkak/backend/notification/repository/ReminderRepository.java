package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.Reminder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReminderRepository {

    Reminder save(Reminder reminder);

    Reminder saveAndFlush(Reminder reminder);

    Optional<Reminder> findByIdForUpdate(UUID reminderId);

    ReminderSlice findReminders(int page, int pageSize);

    List<Reminder> findActiveReminders();

    Optional<Reminder> findActiveForPublication(UUID reminderId);
}
