package com.chalkak.backend.admin.service.notification;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.notification.domain.TopicPushSchedule;
import com.chalkak.backend.notification.repository.TopicPushScheduleRepository;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminTopicPushScheduleService {

    private final TopicPushScheduleRepository scheduleRepository;

    @Transactional(readOnly = true)
    public List<TopicPushScheduleResult> getSchedules() {
        List<TopicPushSchedule> schedules = scheduleRepository.findAllByOrderBySendTimeAsc();
        List<TopicPushScheduleResult> results = new ArrayList<>(schedules.size());
        for (TopicPushSchedule schedule : schedules) {
            results.add(TopicPushScheduleResult.from(schedule));
        }
        return results;
    }

    @Transactional
    public TopicPushScheduleResult createSchedule(LocalTime sendTime) {
        validateSendTimeAvailable(sendTime);
        TopicPushSchedule schedule = new TopicPushSchedule(sendTime);
        return TopicPushScheduleResult.from(scheduleRepository.saveAndFlush(schedule));
    }

    @Transactional
    public TopicPushScheduleResult updateSchedule(UUID scheduleId, LocalTime sendTime) {
        TopicPushSchedule schedule = getSchedule(scheduleId);
        if (schedule.getSendTime().equals(sendTime)) {
            return TopicPushScheduleResult.from(schedule);
        }
        validateSendTimeAvailable(sendTime);
        schedule.updateSendTime(sendTime);
        return TopicPushScheduleResult.from(scheduleRepository.saveAndFlush(schedule));
    }

    @Transactional
    public void deleteSchedule(UUID scheduleId) {
        TopicPushSchedule schedule = getSchedule(scheduleId);
        scheduleRepository.delete(schedule);
    }

    private TopicPushSchedule getSchedule(UUID scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.BUSINESS_ERROR,
                        "주제 푸시 설정을 찾을 수 없습니다."));
    }

    private void validateSendTimeAvailable(LocalTime sendTime) {
        if (scheduleRepository.existsBySendTime(sendTime)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "해당 시각의 주제 푸시 설정이 이미 존재합니다.");
        }
    }
}
