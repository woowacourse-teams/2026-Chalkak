package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.TopicPushSchedule;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TopicPushScheduleRepository {

    List<TopicPushSchedule> findAllByOrderBySendTimeAsc();

    Optional<TopicPushSchedule> findById(UUID scheduleId);

    Optional<TopicPushSchedule> findByIdForUpdate(UUID scheduleId);

    boolean existsBySendTime(LocalTime sendTime);

    TopicPushSchedule saveAndFlush(TopicPushSchedule schedule);

    void delete(TopicPushSchedule schedule);
}
