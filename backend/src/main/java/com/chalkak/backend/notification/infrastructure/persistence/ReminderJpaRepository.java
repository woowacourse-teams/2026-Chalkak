package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.Reminder;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderJpaRepository extends JpaRepository<Reminder, UUID> {

    Slice<Reminder> findSliceBy(Pageable pageable);
}
