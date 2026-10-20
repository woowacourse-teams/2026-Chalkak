package com.chalkak.backend.admin.service.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminNoticeAuditTransactionTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000594");
    private static final UUID UNKNOWN_ADMIN_ID = UUID
            .fromString("019a0000-0000-7000-8000-000000000593");

    @Autowired
    private AdminNoticeService noticeService;

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
    @DisplayName("감사 로그 저장이 실패하면 공지 등록도 함께 롤백한다")
    void createNotice_auditInsertFails_rollsBackNotice() {
        // When & Then
        assertThatThrownBy(() -> noticeService.createNotice(
                UNKNOWN_ADMIN_ID,
                "서비스 점검 안내",
                "점검합니다.",
                Instant.now()
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(countRows("notices")).isZero();
    }

    @Test
    @DisplayName("감사 로그가 민감 정보로 판정하는 형식을 제목에 포함하면 공지 등록도 함께 롤백한다")
    void createNotice_sensitiveTitle_rollsBackNotice() {
        // Given
        insertAdmin();

        // When & Then
        assertThatThrownBy(() -> noticeService.createNotice(
                ADMIN_ID,
                "password: 8자 이상",
                "비밀번호 정책이 변경됩니다.",
                Instant.now()
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("민감한 정보는 관리자 감사 로그에 저장할 수 없습니다.");
        assertThat(countRows("notices")).isZero();
        assertThat(countRows("admin_audit_logs")).isZero();
    }

    @Test
    @DisplayName("감사 로그가 민감 정보로 판정하는 형식을 수정 제목에 포함하면 공지 수정도 함께 롤백한다")
    void updateNotice_sensitiveTitle_rollsBackUpdate() {
        // Given
        insertAdmin();
        UUID noticeId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO notices (title, body, scheduled_at, created_at, updated_at)
                        VALUES ('서비스 점검 안내', '점검합니다.', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        RETURNING id
                        """,
                UUID.class,
                Timestamp.from(Instant.now().plusSeconds(3600))
        );

        // When & Then
        assertThatThrownBy(() -> noticeService.updateNotice(
                noticeId,
                ADMIN_ID,
                "password: 8자 이상",
                "비밀번호 정책이 변경됩니다.",
                Instant.now()
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("민감한 정보는 관리자 감사 로그에 저장할 수 없습니다.");
        assertThat(jdbcTemplate.queryForObject("SELECT title FROM notices", String.class))
                .isEqualTo("서비스 점검 안내");
        assertThat(countRows("admin_audit_logs")).isZero();
    }

    @Test
    @DisplayName("감사 로그 저장이 실패하면 공지 삭제도 함께 롤백한다")
    void deleteNotice_auditInsertFails_rollsBackDeletion() {
        // Given
        UUID noticeId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO notices (title, body, scheduled_at, created_at, updated_at)
                        VALUES ('서비스 점검 안내', '점검합니다.', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        RETURNING id
                        """,
                UUID.class,
                Timestamp.from(Instant.now().plusSeconds(3600))
        );

        // When & Then
        assertThatThrownBy(() -> noticeService.deleteNotice(
                noticeId,
                UNKNOWN_ADMIN_ID,
                "점검 일정 취소"
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NULL FROM notices", Boolean.class)).isTrue();
    }

    private void insertAdmin() {
        jdbcTemplate.update(
                """
                        INSERT INTO admins (id, username, password, created_at, updated_at)
                        VALUES (?, 'notice-audit-manager', 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
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
