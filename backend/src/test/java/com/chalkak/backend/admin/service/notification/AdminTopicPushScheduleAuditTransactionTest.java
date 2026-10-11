package com.chalkak.backend.admin.service.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AdminTopicPushScheduleAuditTransactionTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000903");
    private static final UUID UNKNOWN_ADMIN_ID = UUID
            .fromString("019a0000-0000-7000-8000-000000000904");

    @Autowired
    private AdminTopicPushScheduleService scheduleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        clean();
        jdbcTemplate.update(
                """
                        INSERT INTO admins (id, username, password, created_at, updated_at)
                        VALUES (?, 'push-schedule-audit-manager', 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                ADMIN_ID);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATE", "UPDATE", "DELETE"})
    @DisplayName("감사 로그 저장이 실패하면 주제 푸시 설정 변경도 함께 롤백한다")
    void mutateSchedule_auditInsertFails_rollsBackSchedule(String operation) {
        // Given
        UUID scheduleId = prepareSchedule(operation);

        // When & Then
        assertThatThrownBy(() -> mutateSchedule(operation, scheduleId, UNKNOWN_ADMIN_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertOriginalState(operation, scheduleId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATE", "UPDATE", "DELETE"})
    @DisplayName("업무 트랜잭션이 실패하면 설정과 감사 로그를 함께 롤백한다")
    void mutateSchedule_businessTransactionFails_rollsBackScheduleAndAudit(String operation) {
        // Given
        UUID scheduleId = prepareSchedule(operation);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        // When & Then
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            mutateSchedule(operation, scheduleId, ADMIN_ID);
            throw new IllegalStateException("업무 트랜잭션 실패");
        })).isInstanceOf(IllegalStateException.class);
        assertOriginalState(operation, scheduleId);
    }

    private UUID prepareSchedule(String operation) {
        if (operation.equals("CREATE")) {
            return null;
        }
        return scheduleService.createSchedule(ADMIN_ID, LocalTime.of(9, 0)).scheduleId();
    }

    private void mutateSchedule(String operation, UUID scheduleId, UUID adminId) {
        if (operation.equals("CREATE")) {
            scheduleService.createSchedule(adminId, LocalTime.of(9, 0));
            return;
        }
        if (operation.equals("UPDATE")) {
            scheduleService.updateSchedule(scheduleId, adminId, LocalTime.of(18, 0));
            return;
        }
        scheduleService.deleteSchedule(scheduleId, adminId);
    }

    private void assertOriginalState(String operation, UUID scheduleId) {
        if (operation.equals("CREATE")) {
            assertThat(scheduleService.getSchedules()).isEmpty();
            assertThat(countAuditLogs()).isZero();
            return;
        }
        assertThat(scheduleService.getSchedules()).hasSize(1);
        assertThat(scheduleService.getSchedules().getFirst().scheduleId()).isEqualTo(scheduleId);
        assertThat(scheduleService.getSchedules().getFirst().sendTime())
                .isEqualTo(LocalTime.of(9, 0));
        assertThat(countAuditLogs()).isEqualTo(1);
    }

    private int countAuditLogs() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM admin_audit_logs WHERE actor_admin_id = ?", Integer.class,
                ADMIN_ID);
    }

    private void clean() {
        new DatabaseCleaner(jdbcTemplate).clean();
        jdbcTemplate.update("DELETE FROM admin_audit_logs WHERE actor_admin_id = ?", ADMIN_ID);
        jdbcTemplate.update("DELETE FROM admins WHERE id = ?", ADMIN_ID);
    }
}
