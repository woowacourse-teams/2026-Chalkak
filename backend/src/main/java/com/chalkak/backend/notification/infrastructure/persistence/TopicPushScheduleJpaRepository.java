package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.TopicPushSchedule;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicPushScheduleJpaRepository extends JpaRepository<TopicPushSchedule, UUID> {

    boolean existsBySendTime(LocalTime sendTime);
}
