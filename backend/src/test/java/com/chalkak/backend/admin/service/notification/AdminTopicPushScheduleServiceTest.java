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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import com.chalkak.backend.admin.domain.AdminAuditLog;
import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.common.logging.LogFields;
import org.slf4j.MDC;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminTopicPushScheduleServiceTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000902");

    @Autowired
    private AdminTopicPushScheduleService scheduleService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID requestId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MDC.put(LogFields.REQUEST_ID, requestId.toString());
        jdbcTemplate.update(
                """
                        INSERT INTO admins (id, username, password, created_at, updated_at)
                        VALUES (?, 'push-schedule-manager', 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                ADMIN_ID);
    }

    @AfterEach
    void tearDown() {
        MDC.remove(LogFields.REQUEST_ID);
    }

    @Test
    @DisplayName("설정 전체를 등록 순서와 관계없이 발송 시각 오름차순으로 조회한다")
    void getSchedules_multipleSchedules_returnsAllOrderedBySendTime() {
        // Given
        scheduleService.createSchedule(ADMIN_ID, LocalTime.of(18, 0));
        scheduleService.createSchedule(ADMIN_ID, LocalTime.of(9, 0));
        scheduleService.createSchedule(ADMIN_ID, LocalTime.of(8, 30));
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
        TopicPushScheduleResult result = scheduleService.createSchedule(ADMIN_ID,
                LocalTime.of(9, 0));
        entityManager.flush();
        entityManager.clear();

        // Then
        TopicPushSchedule stored = entityManager.find(TopicPushSchedule.class, result.scheduleId());
        assertThat(stored.getSendTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(result.createdAt()).isNotNull();
        assertThat(result.updatedAt()).isNotNull();
        AdminAuditLog auditLog = getAuditLog(result.scheduleId(),
                AdminAction.TOPIC_PUSH_SCHEDULE_CREATED);
        assertThat(auditLog.getActorAdminId()).isEqualTo(ADMIN_ID);
        assertThat(auditLog.getTargetType()).isEqualTo(AdminTargetType.TOPIC_PUSH_SCHEDULE);
        assertThat(auditLog.getBeforeState()).isEmpty();
        assertThat(auditLog.getAfterState())
                .containsExactlyEntriesOf(Map.of("sendTime", "09:00:00"));
        assertThat(auditLog.getReason()).isNull();
        assertThat(auditLog.getOccurredAt()).isNotNull();
        assertThat(auditLog.getRequestId()).isEqualTo(requestId);
    }

    @Test
    @DisplayName("서로 다른 시각을 여러 개 등록할 수 있다")
    void createSchedule_differentTimes_persistsMultipleSchedules() {
        // When
        TopicPushScheduleResult first = scheduleService.createSchedule(ADMIN_ID,
                LocalTime.of(8, 0));
        TopicPushScheduleResult second = scheduleService.createSchedule(ADMIN_ID,
                LocalTime.of(18, 0));
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
        scheduleService.createSchedule(ADMIN_ID, LocalTime.of(9, 0));

        // When & Then
        assertThatThrownBy(() -> scheduleService.createSchedule(ADMIN_ID, LocalTime.of(9, 0)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("해당 시각의 주제 푸시 설정이 이미 존재합니다.");
        assertThat(countAuditLogs(AdminAction.TOPIC_PUSH_SCHEDULE_CREATED)).isEqualTo(1);
    }

    @Test
    @DisplayName("시각이 잘못되면 DB에 저장하지 않는다")
    void createSchedule_nullSendTime_doesNotPersist() {
        // When & Then
        assertPreparedStatementCount(0, () -> assertThatThrownBy(
                () -> scheduleService.createSchedule(ADMIN_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("주제 푸시 발송 시각은 분 단위의 KST 시각이어야 합니다."));
        assertThat(entityManager.createQuery("select count(s) from TopicPushSchedule s", Long.class)
                .getSingleResult()).isZero();
    }

    @Test
    @DisplayName("초 단위 생성 시각은 중복 조회 없이 거절하고 설정·감사 기록을 남기지 않는다")
    void createSchedule_nonZeroSecond_doesNotQueryOrPersist() {
        // When & Then
        assertPreparedStatementCount(0, () -> assertThatThrownBy(
                () -> scheduleService.createSchedule(ADMIN_ID, LocalTime.of(9, 0, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("주제 푸시 발송 시각은 분 단위의 KST 시각이어야 합니다."));
        assertThat(scheduleService.getSchedules()).isEmpty();
        assertThat(countAuditLogs(AdminAction.TOPIC_PUSH_SCHEDULE_CREATED)).isZero();
    }

    @Test
    @DisplayName("잘못된 수정 시각은 대상 조회 후 중복 조회 없이 거절하고 기존 설정을 유지한다")
    void updateSchedule_nonZeroSecond_preservesScheduleWithoutDuplicateQuery() {
        // Given
        TopicPushScheduleResult original = scheduleService.createSchedule(ADMIN_ID,
                LocalTime.of(9, 0));
        entityManager.clear();

        // When & Then
        assertPreparedStatementCount(1, () -> assertThatThrownBy(
                () -> scheduleService.updateSchedule(original.scheduleId(), ADMIN_ID,
                        LocalTime.of(18, 0, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("주제 푸시 발송 시각은 분 단위의 KST 시각이어야 합니다."));
        entityManager.clear();
        assertThat(entityManager.find(TopicPushSchedule.class, original.scheduleId()).getSendTime())
                .isEqualTo(LocalTime.of(9, 0));
        assertThat(countAuditLogs(AdminAction.TOPIC_PUSH_SCHEDULE_UPDATED)).isZero();
    }

    @Test
    @DisplayName("시각을 수정하면 식별자·생성 시각을 유지하고 DB에 반영한다")
    void updateSchedule_validTime_persistsChangedTime() {
        // Given
        TopicPushScheduleResult original = scheduleService.createSchedule(ADMIN_ID,
                LocalTime.of(9, 0));
        entityManager.clear();

        Instant createdAt = entityManager.find(TopicPushSchedule.class, original.scheduleId())
                .getCreatedAt();

        // When
        TopicPushScheduleResult result = scheduleService.updateSchedule(original.scheduleId(),
                ADMIN_ID, LocalTime.of(18, 30));
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
        AdminAuditLog auditLog = getAuditLog(original.scheduleId(),
                AdminAction.TOPIC_PUSH_SCHEDULE_UPDATED);
        assertThat(auditLog.getActorAdminId()).isEqualTo(ADMIN_ID);
        assertThat(auditLog.getBeforeState())
                .containsExactlyEntriesOf(Map.of("sendTime", "09:00:00"));
        assertThat(auditLog.getAfterState())
                .containsExactlyEntriesOf(Map.of("sendTime", "18:30:00"));
    }

    @Test
    @DisplayName("현재 설정 시각으로 다시 수정해도 중복 오류 없이 같은 결과를 반환한다")
    void updateSchedule_sameTime_returnsUnchangedSchedule() {
        // Given
        TopicPushScheduleResult original = scheduleService.createSchedule(ADMIN_ID,
                LocalTime.of(9, 0));

        // When
        TopicPushScheduleResult result = scheduleService.updateSchedule(original.scheduleId(),
                ADMIN_ID, LocalTime.of(9, 0));

        // Then
        assertThat(result).isEqualTo(original);
        assertThat(countAuditLogs(AdminAction.TOPIC_PUSH_SCHEDULE_UPDATED)).isZero();
        assertThat(scheduleService.getSchedules()).hasSize(1);
    }

    @Test
    @DisplayName("다른 설정의 시각으로 수정할 수 없다")
    void updateSchedule_duplicateTime_preservesBothSchedules() {
        // Given
        TopicPushScheduleResult original = scheduleService.createSchedule(ADMIN_ID,
                LocalTime.of(9, 0));
        scheduleService.createSchedule(ADMIN_ID, LocalTime.of(18, 0));

        // When & Then
        assertThatThrownBy(
                () -> scheduleService.updateSchedule(original.scheduleId(), ADMIN_ID,
                        LocalTime.of(18, 0)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("해당 시각의 주제 푸시 설정이 이미 존재합니다.");
        assertThat(scheduleService.getSchedules()).extracting(TopicPushScheduleResult::sendTime)
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(18, 0));
        assertThat(countAuditLogs(AdminAction.TOPIC_PUSH_SCHEDULE_UPDATED)).isZero();
    }

    @Test
    @DisplayName("없는 설정을 수정하면 찾을 수 없음 오류로 거절한다")
    void updateSchedule_missingSchedule_throwsNotFoundException() {
        // When & Then
        assertThatThrownBy(
                () -> scheduleService.updateSchedule(UUID.randomUUID(), ADMIN_ID,
                        LocalTime.of(9, 0)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("주제 푸시 설정을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("설정을 삭제하면 DB에서 제거하고 다른 설정은 유지한다")
    void deleteSchedule_existingSchedule_deletesOnlyTarget() {
        // Given
        TopicPushScheduleResult target = scheduleService.createSchedule(ADMIN_ID,
                LocalTime.of(9, 0));
        TopicPushScheduleResult remaining = scheduleService.createSchedule(ADMIN_ID,
                LocalTime.of(18, 0));

        // When
        scheduleService.deleteSchedule(target.scheduleId(), ADMIN_ID);
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(entityManager.find(TopicPushSchedule.class, target.scheduleId())).isNull();
        assertThat(entityManager.find(TopicPushSchedule.class, remaining.scheduleId())).isNotNull();
        assertThat(scheduleService.getSchedules()).extracting(TopicPushScheduleResult::scheduleId)
                .containsExactly(remaining.scheduleId());
        AdminAuditLog auditLog = getAuditLog(target.scheduleId(),
                AdminAction.TOPIC_PUSH_SCHEDULE_DELETED);
        assertThat(auditLog.getActorAdminId()).isEqualTo(ADMIN_ID);
        assertThat(auditLog.getBeforeState())
                .containsExactlyEntriesOf(Map.of("sendTime", "09:00:00"));
        assertThat(auditLog.getAfterState()).isEmpty();
        assertThat(getAuditLog(target.scheduleId(), AdminAction.TOPIC_PUSH_SCHEDULE_CREATED))
                .isNotNull();
    }

    @Test
    @DisplayName("삭제한 발송 시각은 새 설정으로 다시 등록할 수 있다")
    void deleteSchedule_deletedTime_allowsRecreation() {
        // Given
        TopicPushScheduleResult original = scheduleService.createSchedule(ADMIN_ID,
                LocalTime.of(9, 0));
        scheduleService.deleteSchedule(original.scheduleId(), ADMIN_ID);
        entityManager.flush();
        entityManager.clear();

        // When
        TopicPushScheduleResult recreated = scheduleService.createSchedule(ADMIN_ID,
                LocalTime.of(9, 0));
        entityManager.clear();

        // Then
        assertThat(recreated.scheduleId()).isNotEqualTo(original.scheduleId());
        assertThat(entityManager.find(TopicPushSchedule.class, original.scheduleId())).isNull();
        assertThat(
                entityManager.find(TopicPushSchedule.class, recreated.scheduleId()).getSendTime())
                .isEqualTo(LocalTime.of(9, 0));
    }

    private void assertPreparedStatementCount(long expectedCount, Runnable operation) {
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class)
                .getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        long beforeCount = statistics.getPrepareStatementCount();
        try {
            operation.run();
            assertThat(statistics.getPrepareStatementCount() - beforeCount)
                    .isEqualTo(expectedCount);
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    private AdminAuditLog getAuditLog(UUID scheduleId, AdminAction action) {
        entityManager.flush();
        return entityManager.createQuery("""
                SELECT audit FROM AdminAuditLog audit
                WHERE audit.targetId = :scheduleId AND audit.action = :action
                """, AdminAuditLog.class)
                .setParameter("scheduleId", scheduleId)
                .setParameter("action", action)
                .getSingleResult();
    }

    private long countAuditLogs(AdminAction action) {
        entityManager.flush();
        return entityManager
                .createQuery(
                        "SELECT count(audit) FROM AdminAuditLog audit WHERE audit.action = :action",
                        Long.class)
                .setParameter("action", action).getSingleResult();
    }

    @Test
    @DisplayName("관리자 식별자가 없으면 설정과 감사 로그를 생성하지 않는다")
    void createSchedule_nullAdminId_doesNotPersistScheduleOrAudit() {
        // When & Then
        assertThatThrownBy(() -> scheduleService.createSchedule(null, LocalTime.of(9, 0)))
                .isInstanceOf(BusinessException.class);
        assertThat(scheduleService.getSchedules()).isEmpty();
        assertThat(countAuditLogs(AdminAction.TOPIC_PUSH_SCHEDULE_CREATED)).isZero();
    }

    @Test
    @DisplayName("없는 설정을 삭제하면 찾을 수 없음 오류로 거절한다")
    void deleteSchedule_missingSchedule_throwsNotFoundException() {
        // When & Then
        assertThatThrownBy(() -> scheduleService.deleteSchedule(UUID.randomUUID(), ADMIN_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("주제 푸시 설정을 찾을 수 없습니다.");
    }
}
