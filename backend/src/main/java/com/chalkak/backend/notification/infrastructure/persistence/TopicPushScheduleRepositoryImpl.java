package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.notification.domain.TopicPushSchedule;
import com.chalkak.backend.notification.repository.TopicPushScheduleRepository;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TopicPushScheduleRepositoryImpl implements TopicPushScheduleRepository {

    private static final String SEND_TIME_UNIQUE_CONSTRAINT = "ux_topic_push_schedules_send_time";

    private final TopicPushScheduleJpaRepository jpaRepository;

    @Override
    public List<TopicPushSchedule> findAllByOrderBySendTimeAsc() {
        return jpaRepository.findAllByOrderBySendTimeAsc();
    }

    @Override
    public Optional<TopicPushSchedule> findById(UUID scheduleId) {
        return jpaRepository.findById(scheduleId);
    }

    @Override
    public boolean existsBySendTime(LocalTime sendTime) {
        return jpaRepository.existsBySendTime(sendTime);
    }

    @Override
    public Optional<TopicPushSchedule> findByIdForUpdate(UUID scheduleId) {
        return jpaRepository.findByIdForUpdate(scheduleId);
    }

    @Override
    public TopicPushSchedule saveAndFlush(TopicPushSchedule schedule) {
        try {
            return jpaRepository.saveAndFlush(schedule);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateSendTime(exception)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "해당 시각의 주제 푸시 설정이 이미 존재합니다.");
            }
            throw exception;
        }
    }

    @Override
    public void delete(TopicPushSchedule schedule) {
        jpaRepository.delete(schedule);
    }

    private boolean isDuplicateSendTime(DataIntegrityViolationException exception) {
        if (!(exception.getMostSpecificCause() instanceof PSQLException postgresException)) {
            return false;
        }
        ServerErrorMessage error = postgresException.getServerErrorMessage();
        return error != null && SEND_TIME_UNIQUE_CONSTRAINT.equals(error.getConstraint());
    }
}
