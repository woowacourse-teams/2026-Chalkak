package com.chalkak.backend.admin.service.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.notification.domain.ReminderRepeat;
import com.chalkak.backend.notification.domain.ReminderTarget;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminReminderAuditTransactionTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000730");
    private static final UUID UNKNOWN_ADMIN_ID = UUID
            .fromString("019a0000-0000-7000-8000-000000000731");

    @Autowired
    private AdminReminderService reminderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        clean();
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    @DisplayName("감사 로그 저장이 실패하면 리마인더 등록도 함께 롤백한다")
    void createReminder_auditInsertFails_rollsBackReminder() {
        // When & Then
        assertThatThrownBy(() -> reminderService.createReminder(
                UNKNOWN_ADMIN_ID,
                "오늘이 얼마 남지 않았어요",
                "사진을 자랑해 주세요!",
                ReminderRepeat.ONCE,
                ReminderTarget.ALL,
                Instant.now()
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(countRows("reminders")).isZero();
    }

    @Test
    @DisplayName("감사 로그가 민감 정보로 판정하는 형식을 제목에 포함하면 리마인더 등록도 함께 롤백한다")
    void createReminder_sensitiveTitle_rollsBackReminder() {
        // Given
        insertAdmin();

        // When & Then
        assertThatThrownBy(() -> reminderService.createReminder(
                ADMIN_ID,
                "password: 8자 이상",
                "비밀번호 정책이 변경됩니다.",
                ReminderRepeat.ONCE,
                ReminderTarget.ALL,
                Instant.now()
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("민감한 정보는 관리자 감사 로그에 저장할 수 없습니다.");
        assertThat(countRows("reminders")).isZero();
        assertThat(countRows("admin_audit_logs")).isZero();
    }

    private void insertAdmin() {
        jdbcTemplate.update(
                """
                        INSERT INTO admins (id, username, password, created_at, updated_at)
                        VALUES (?, 'reminder-audit-manager', 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                ADMIN_ID);
    }

    private int countRows(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void clean() {
        new DatabaseCleaner(jdbcTemplate).clean();
        jdbcTemplate.update("DELETE FROM admin_audit_logs WHERE actor_admin_id = ?", ADMIN_ID);
        jdbcTemplate.update("DELETE FROM admins WHERE id = ?", ADMIN_ID);
    }
}
