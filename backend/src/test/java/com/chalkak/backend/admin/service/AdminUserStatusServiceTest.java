package com.chalkak.backend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.UserStatus;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminUserStatusServiceTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID =
            UUID.fromString("0198fd10-0000-7000-8000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("0198fd10-0000-7000-8000-000000000002");
    private static final UUID UNKNOWN_USER_ID =
            UUID.fromString("0198fd10-0000-7000-8000-000000000099");
    private static final String REASON = "반복적인 운영 정책 위반";
    @Autowired
    private AdminUserStatusService adminUserStatusService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO admins (id, username, password, created_at, updated_at)
                VALUES (?, 'user-manager', 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ADMIN_ID);
        insertUser("ACTIVE", null);
    }

    @Test
    @DisplayName("소셜 계정이 없는 활성 사용자도 관리자가 차단하면 상태와 감사 로그를 저장한다")
    void updateStatus_activeUserWithoutSocialAccount_updatesStatusAndAuditLog() {
        // When
        AdminUserStatusResult result = adminUserStatusService.updateStatus(
                USER_ID,
                ADMIN_ID,
                UserStatus.BANNED,
                "  " + REASON + "  "
        );
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(result).isEqualTo(new AdminUserStatusResult(USER_ID, UserStatus.BANNED));
        assertThat(findUserStatus()).isEqualTo("BANNED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM admin_audit_logs
                WHERE actor_admin_id = ?
                  AND action = CAST('USER_BANNED' AS admin_action)
                  AND target_type = CAST('USER' AS admin_target_type)
                  AND target_id = ?
                  AND reason = ?
                  AND before_state ->> 'status' = 'ACTIVE'
                  AND after_state ->> 'status' = 'BANNED'
                """, Integer.class, ADMIN_ID, USER_ID, REASON)).isEqualTo(1);
    }

    @Test
    @DisplayName("소셜 계정이 없는 차단 사용자도 관리자가 차단 해제하면 상태와 감사 로그를 갱신한다")
    void updateStatus_bannedUserWithoutSocialAccount_updatesStatusAndAuditLog() {
        // Given
        jdbcTemplate.update("UPDATE users SET status = 'BANNED' WHERE id = ?", USER_ID);

        // When
        AdminUserStatusResult result = adminUserStatusService.updateStatus(
                USER_ID,
                ADMIN_ID,
                UserStatus.ACTIVE,
                "이의 신청 검토 완료"
        );
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(findUserStatus()).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admin_audit_logs
                WHERE target_id = ?
                  AND action = CAST('USER_UNBANNED' AS admin_action)
                  AND before_state ->> 'status' = 'BANNED'
                  AND after_state ->> 'status' = 'ACTIVE'
                """, Integer.class, USER_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 같은 상태인 사용자는 상태를 다시 변경하지 않는다")
    void updateStatus_sameStatus_throwsStateChanged() {
        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> adminUserStatusService.updateStatus(
                        USER_ID,
                        ADMIN_ID,
                        UserStatus.ACTIVE,
                        REASON)
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_STATE_CHANGED);
        assertThat(countAuditLogs()).isZero();
    }

    @Test
    @DisplayName("탈퇴 사용자의 상태는 변경하지 않는다")
    void updateStatus_withdrawnUser_throwsBusinessException() {
        // Given
        jdbcTemplate.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", USER_ID);

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> adminUserStatusService.updateStatus(
                        USER_ID,
                        ADMIN_ID,
                        UserStatus.BANNED,
                        REASON)
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(countAuditLogs()).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 상태 변경은 404로 거절한다")
    void updateStatus_unknownUser_throwsNotFoundException() {
        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> adminUserStatusService.updateStatus(
                        UNKNOWN_USER_ID,
                        ADMIN_ID,
                        UserStatus.BANNED,
                        REASON)
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
    }

    @Test
    @DisplayName("상태 변경 사유가 비어 있으면 사용자를 변경하지 않는다")
    void updateStatus_blankReason_throwsBusinessException() {
        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> adminUserStatusService.updateStatus(
                        USER_ID,
                        ADMIN_ID,
                        UserStatus.BANNED,
                        "  ")
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(findUserStatus()).isEqualTo("ACTIVE");
        assertThat(countAuditLogs()).isZero();
    }

    private void insertUser(String status, String deletedAt) {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    created_at, updated_at, deleted_at
                ) VALUES (
                    ?, 'status-target@example.com', CAST(? AS user_status),
                    'signatures/status-target', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    CAST(? AS timestamptz)
                )
                """, USER_ID, status, deletedAt);
    }

    private String findUserStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT CAST(status AS TEXT) FROM users WHERE id = ?",
                String.class,
                USER_ID
        );
    }

    private int countAuditLogs() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs WHERE target_id = ?",
                Integer.class,
                USER_ID
        );
    }
}
