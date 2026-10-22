package com.chalkak.backend.admin.service.notification;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.service.audit.AdminAuditLogCommand;
import com.chalkak.backend.admin.service.audit.AdminAuditLogService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.notification.domain.TopicPushSchedule;
import com.chalkak.backend.notification.repository.TopicPushScheduleRepository;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminTopicPushScheduleService {

    private static final DateTimeFormatter SEND_TIME_FORMAT = DateTimeFormatter
            .ofPattern("HH:mm:ss");

    private final TopicPushScheduleRepository scheduleRepository;
    private final AdminAuditLogService adminAuditLogService;

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
    public TopicPushScheduleResult createSchedule(UUID adminId, LocalTime sendTime) {
        validateAdminId(adminId);
        TopicPushSchedule.validateSendTime(sendTime);
        validateSendTimeAvailable(sendTime);
        TopicPushSchedule schedule = new TopicPushSchedule(sendTime);
        TopicPushSchedule saved = scheduleRepository.saveAndFlush(schedule);
        createAuditLog(
                adminId,
                AdminAction.TOPIC_PUSH_SCHEDULE_CREATED,
                saved.getId(),
                AdminAuditSnapshot.from(Map.of()),
                createScheduleState(saved)
        );
        return TopicPushScheduleResult.from(saved);
    }

    @Transactional
    public TopicPushScheduleResult updateSchedule(
            UUID scheduleId,
            UUID adminId,
            LocalTime sendTime
    ) {
        validateAdminId(adminId);
        TopicPushSchedule schedule = getSchedule(scheduleId);
        TopicPushSchedule.validateSendTime(sendTime);
        if (schedule.getSendTime().equals(sendTime)) {
            return TopicPushScheduleResult.from(schedule);
        }
        validateSendTimeAvailable(sendTime);
        AdminAuditSnapshot beforeState = createScheduleState(schedule);
        schedule.updateSendTime(sendTime);
        TopicPushSchedule saved = scheduleRepository.saveAndFlush(schedule);
        createAuditLog(
                adminId,
                AdminAction.TOPIC_PUSH_SCHEDULE_UPDATED,
                saved.getId(),
                beforeState,
                createScheduleState(saved)
        );
        return TopicPushScheduleResult.from(saved);
    }

    @Transactional
    public void deleteSchedule(UUID scheduleId, UUID adminId) {
        validateAdminId(adminId);
        TopicPushSchedule schedule = getSchedule(scheduleId);
        AdminAuditSnapshot beforeState = createScheduleState(schedule);
        scheduleRepository.delete(schedule);
        createAuditLog(
                adminId,
                AdminAction.TOPIC_PUSH_SCHEDULE_DELETED,
                scheduleId,
                beforeState,
                AdminAuditSnapshot.from(Map.of())
        );
    }

    private TopicPushSchedule getSchedule(UUID scheduleId) {
        return scheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.BUSINESS_ERROR,
                        "주제 푸시 설정을 찾을 수 없습니다."));
    }

    private void validateSendTimeAvailable(LocalTime sendTime) {
        if (scheduleRepository.existsBySendTime(sendTime)) {
            throw TopicPushSchedule.duplicateSendTimeException();
        }
    }

    private AdminAuditSnapshot createScheduleState(TopicPushSchedule schedule) {
        return AdminAuditSnapshot
                .from(Map.of("sendTime", schedule.getSendTime().format(SEND_TIME_FORMAT)));
    }

    private void createAuditLog(
            UUID adminId,
            AdminAction action,
            UUID scheduleId,
            AdminAuditSnapshot beforeState,
            AdminAuditSnapshot afterState
    ) {
        adminAuditLogService.createAuditLog(new AdminAuditLogCommand(
                adminId,
                action,
                AdminTargetType.TOPIC_PUSH_SCHEDULE,
                scheduleId,
                null,
                beforeState,
                afterState
        ));
    }

    private void validateAdminId(UUID adminId) {
        if (adminId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "관리자 식별자가 필요합니다.");
        }
    }
}
