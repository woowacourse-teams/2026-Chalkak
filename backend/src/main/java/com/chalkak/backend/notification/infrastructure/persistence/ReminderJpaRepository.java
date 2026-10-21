package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.Reminder;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderJpaRepository extends JpaRepository<Reminder, UUID> {
}
