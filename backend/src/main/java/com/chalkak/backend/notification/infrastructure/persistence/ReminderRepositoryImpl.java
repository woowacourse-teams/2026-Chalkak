package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.Reminder;
import com.chalkak.backend.notification.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReminderRepositoryImpl implements ReminderRepository {

    private final ReminderJpaRepository reminderJpaRepository;

    @Override
    public Reminder save(Reminder reminder) {
        return reminderJpaRepository.save(reminder);
    }
}
