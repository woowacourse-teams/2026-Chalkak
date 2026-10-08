package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.TopicPushSchedule;
import java.time.LocalTime;

public interface TopicPushScheduleRepository {

    boolean existsBySendTime(LocalTime sendTime);

    TopicPushSchedule saveAndFlush(TopicPushSchedule schedule);
}
