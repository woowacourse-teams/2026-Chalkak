package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.TopicPushSchedule;
import java.time.LocalTime;
import java.util.List;

public interface TopicPushScheduleRepository {

    List<TopicPushSchedule> findAllByOrderBySendTimeAsc();

    boolean existsBySendTime(LocalTime sendTime);

    TopicPushSchedule saveAndFlush(TopicPushSchedule schedule);
}
