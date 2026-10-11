package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.TopicPushSchedule;
import jakarta.persistence.LockModeType;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicPushScheduleJpaRepository extends JpaRepository<TopicPushSchedule, UUID> {

    List<TopicPushSchedule> findAllByOrderBySendTimeAsc();

    boolean existsBySendTime(LocalTime sendTime);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT schedule FROM TopicPushSchedule schedule WHERE schedule.id = :scheduleId")
    Optional<TopicPushSchedule> findByIdForUpdate(@Param("scheduleId") UUID scheduleId);
}
