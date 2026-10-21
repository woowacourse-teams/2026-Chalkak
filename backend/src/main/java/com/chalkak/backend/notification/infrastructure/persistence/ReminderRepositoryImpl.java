package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.Reminder;
import com.chalkak.backend.notification.repository.ReminderRepository;
import com.chalkak.backend.notification.repository.ReminderSlice;
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
    public ReminderSlice findReminders(int page, int pageSize) {
        Slice<Reminder> slice = reminderJpaRepository.findSliceBy(
                PageRequest.of(
                        page - 1,
                        pageSize,
                        LATEST_CREATED_FIRST
                ));
        return new ReminderSlice(slice.getContent(), slice.hasNext());
    }
}
