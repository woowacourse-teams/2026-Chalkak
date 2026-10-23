package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.Reminder;
import com.chalkak.backend.notification.repository.ReminderRepository;
import com.chalkak.backend.notification.repository.ReminderSlice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReminderRepositoryImpl implements ReminderRepository {

    private static final Sort LATEST_CREATED_FIRST = Sort.by(
            Sort.Direction.DESC,
            "createdAt",
            "id"
    );

    private final ReminderJpaRepository reminderJpaRepository;

    @Override
    public Reminder save(Reminder reminder) {
        return reminderJpaRepository.save(reminder);
    }

    @Override
    public Reminder saveAndFlush(Reminder reminder) {
        return reminderJpaRepository.saveAndFlush(reminder);
    }

    @Override
    public Optional<Reminder> findByIdForUpdate(UUID reminderId) {
        return reminderJpaRepository.findByIdForUpdate(reminderId);
    }

    @Override
    public List<Reminder> findActiveReminders() {
        return reminderJpaRepository.findByDeletedAtIsNullOrderByIdAsc();
    }

    @Override
    public Optional<Reminder> findActiveById(UUID reminderId) {
        return reminderJpaRepository.findByIdAndDeletedAtIsNull(reminderId);
    }

    @Override
    public Optional<Reminder> findActiveForPublication(UUID reminderId) {
        return reminderJpaRepository.findActiveForPublication(reminderId);
    }

    @Override
    public ReminderSlice findReminders(int page, int pageSize) {
        Slice<Reminder> slice = reminderJpaRepository.findByDeletedAtIsNull(
                PageRequest.of(
                        page - 1,
                        pageSize,
                        LATEST_CREATED_FIRST
                ));
        return new ReminderSlice(slice.getContent(), slice.hasNext());
    }
}
