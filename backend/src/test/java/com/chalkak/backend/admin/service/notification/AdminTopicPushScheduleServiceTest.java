package com.chalkak.backend.admin.service.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.NotFoundException;
import java.util.UUID;
import com.chalkak.backend.notification.domain.TopicPushSchedule;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalTime;
import java.time.Instant;
import java.util.List;
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
    @DisplayName("설정 전체를 등록 순서와 관계없이 발송 시각 오름차순으로 조회한다")
    void getSchedules_multipleSchedules_returnsAllOrderedBySendTime() {
        // Given
        scheduleService.createSchedule(LocalTime.of(18, 0));
        scheduleService.createSchedule(LocalTime.of(9, 0));
        scheduleService.createSchedule(LocalTime.of(8, 30));
        entityManager.flush();
        entityManager.clear();

        // When
        List<TopicPushScheduleResult> results = scheduleService.getSchedules();

        // Then
        assertThat(results).extracting(TopicPushScheduleResult::sendTime)
                .containsExactly(LocalTime.of(8, 30), LocalTime.of(9, 0), LocalTime.of(18, 0));
        assertThat(results).allSatisfy(result -> {
            assertThat(result.scheduleId()).isNotNull();
            assertThat(result.createdAt()).isNotNull();
            assertThat(result.updatedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("등록된 설정이 없으면 빈 목록을 조회한다")
    void getSchedules_noSchedules_returnsEmptyList() {
        // When
        List<TopicPushScheduleResult> results = scheduleService.getSchedules();

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("설정을 생성하면 시각·식별자·생성 시각을 저장한다")
    void createSchedule_validInput_persistsSchedule() {
        // When
        TopicPushScheduleResult result = scheduleService.createSchedule(
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
        TopicPushScheduleResult first = scheduleService.createSchedule(LocalTime.of(8, 0));
        TopicPushScheduleResult second = scheduleService.createSchedule(LocalTime.of(18, 0));
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

    @Test
    @DisplayName("시각을 수정하면 식별자·생성 시각을 유지하고 DB에 반영한다")
    void updateSchedule_validTime_persistsChangedTime() {
        // Given
        TopicPushScheduleResult original = scheduleService.createSchedule(LocalTime.of(9, 0));
        entityManager.clear();

        Instant createdAt = entityManager.find(TopicPushSchedule.class, original.scheduleId())
                .getCreatedAt();

        // When
        TopicPushScheduleResult result = scheduleService.updateSchedule(
                original.scheduleId(), LocalTime.of(18, 30));
        entityManager.clear();

        // Then
        TopicPushSchedule stored = entityManager.find(TopicPushSchedule.class,
                original.scheduleId());
        assertThat(stored.getSendTime()).isEqualTo(LocalTime.of(18, 30));
        assertThat(result.scheduleId()).isEqualTo(original.scheduleId());
        assertThat(result.createdAt()).isEqualTo(createdAt);
        assertThat(stored.getCreatedAt()).isEqualTo(createdAt);
        assertThat(result.updatedAt()).isEqualTo(stored.getUpdatedAt());
        assertThat(result.updatedAt()).isAfterOrEqualTo(original.updatedAt());
    }

    @Test
    @DisplayName("현재 설정 시각으로 다시 수정해도 중복 오류 없이 같은 결과를 반환한다")
    void updateSchedule_sameTime_returnsUnchangedSchedule() {
        // Given
        TopicPushScheduleResult original = scheduleService.createSchedule(LocalTime.of(9, 0));

        // When
        TopicPushScheduleResult result = scheduleService.updateSchedule(
                original.scheduleId(), LocalTime.of(9, 0));

        // Then
        assertThat(result).isEqualTo(original);
        assertThat(scheduleService.getSchedules()).hasSize(1);
    }

    @Test
    @DisplayName("다른 설정의 시각으로 수정할 수 없다")
    void updateSchedule_duplicateTime_preservesBothSchedules() {
        // Given
        TopicPushScheduleResult original = scheduleService.createSchedule(LocalTime.of(9, 0));
        scheduleService.createSchedule(LocalTime.of(18, 0));

        // When & Then
        assertThatThrownBy(
                () -> scheduleService.updateSchedule(original.scheduleId(), LocalTime.of(18, 0)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("해당 시각의 주제 푸시 설정이 이미 존재합니다.");
        assertThat(scheduleService.getSchedules()).extracting(TopicPushScheduleResult::sendTime)
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(18, 0));
    }

    @Test
    @DisplayName("없는 설정을 수정하면 찾을 수 없음 오류로 거절한다")
    void updateSchedule_missingSchedule_throwsNotFoundException() {
        // When & Then
        assertThatThrownBy(
                () -> scheduleService.updateSchedule(UUID.randomUUID(), LocalTime.of(9, 0)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("주제 푸시 설정을 찾을 수 없습니다.");
    }
}
