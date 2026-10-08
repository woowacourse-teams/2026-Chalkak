package com.chalkak.backend.admin.service.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.notification.domain.TopicPushSchedule;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminTopicPushScheduleServiceTest extends IntegrationTestSupport {

    @Autowired
    private AdminTopicPushScheduleService scheduleService;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("설정을 생성하면 시각·식별자·생성 시각을 저장한다")
    void createSchedule_validInput_persistsSchedule() {
        // When
        AdminTopicPushScheduleResult result = scheduleService.createSchedule(
                LocalTime.of(9, 0));
        entityManager.flush();
        entityManager.clear();

        // Then
        TopicPushSchedule stored = entityManager.find(TopicPushSchedule.class, result.scheduleId());
        assertThat(stored.getSendTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(result.createdAt()).isNotNull();
        assertThat(result.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("서로 다른 시각을 여러 개 등록할 수 있다")
    void createSchedule_differentTimes_persistsMultipleSchedules() {
        // When
        AdminTopicPushScheduleResult first = scheduleService.createSchedule(LocalTime.of(8, 0));
        AdminTopicPushScheduleResult second = scheduleService.createSchedule(LocalTime.of(18, 0));
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(first.scheduleId()).isNotEqualTo(second.scheduleId());
        assertThat(entityManager.find(TopicPushSchedule.class, first.scheduleId()).getSendTime())
                .isEqualTo(LocalTime.of(8, 0));
        assertThat(entityManager.find(TopicPushSchedule.class, second.scheduleId()).getSendTime())
                .isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    @DisplayName("같은 시각은 중복으로 등록할 수 없다")
    void createSchedule_duplicateSendTime_throwsBusinessException() {
        // Given
        scheduleService.createSchedule(LocalTime.of(9, 0));

        // When & Then
        assertThatThrownBy(() -> scheduleService.createSchedule(LocalTime.of(9, 0)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("해당 시각의 주제 푸시 설정이 이미 존재합니다.");
    }

    @Test
    @DisplayName("시각이 잘못되면 DB에 저장하지 않는다")
    void createSchedule_nullSendTime_doesNotPersist() {
        // When & Then
        assertThatThrownBy(() -> scheduleService.createSchedule(null))
                .isInstanceOf(BusinessException.class);
        assertThat(entityManager.createQuery("select count(s) from TopicPushSchedule s", Long.class)
                .getSingleResult()).isZero();
    }
}
