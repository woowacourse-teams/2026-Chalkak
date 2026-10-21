package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.Reminder;

public interface ReminderRepository {

    Reminder save(Reminder reminder);
}
