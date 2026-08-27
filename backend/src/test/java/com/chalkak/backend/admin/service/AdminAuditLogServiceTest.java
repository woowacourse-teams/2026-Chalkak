package com.chalkak.backend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AdminAuditLogServiceTest extends IntegrationTestSupport {

    private static final UUID ACTOR_ADMIN_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b657201");
    private static final UUID TARGET_USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b657202");
    private static final UUID UNKNOWN_ADMIN_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b657203");
    private static final String PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Autowired
    private AdminAuditLogService adminAuditLogService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
        transactionTemplate = new TransactionTemplate(transactionManager);
        jdbcTemplate.update("""
                INSERT INTO admins (id, username, password, created_at, updated_at)
                VALUES (?, 'audit-service-admin', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ACTOR_ADMIN_ID, PASSWORD_HASH);
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'audit-service-user@example.com', 'ACTIVE',
                    'chalkak/audit/signatures/user.webp', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, TARGET_USER_ID);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("업무 트랜잭션 안에서 변경과 감사 로그를 함께 저장한다")
    void createAuditLog_withinBusinessTransaction_persistsChangeAndAuditLog() {
        // Given
        UUID requestId = UUID.randomUUID();
        Instant beforeExecution = Instant.now();

        // When
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "UPDATE users SET status = 'BANNED' WHERE id = ?",
                    TARGET_USER_ID
            );
            adminAuditLogService.createAuditLog(createCommand(ACTOR_ADMIN_ID, requestId));
            entityManager.flush();
        });
        Instant afterExecution = Instant.now();

        // Then
        assertThat(userStatus()).isEqualTo("BANNED");
        Map<String, Object> savedAuditLog = jdbcTemplate.queryForMap("""
                SELECT CAST(action AS TEXT) AS action,
                       CAST(target_type AS TEXT) AS target_type,
                       reason,
                       before_state ->> 'status' AS before_status,
                       after_state ->> 'status' AS after_status,
                       occurred_at
                FROM admin_audit_logs
                WHERE request_id = ?
                """, requestId);
        assertThat(savedAuditLog)
                .containsEntry("action", "USER_BANNED")
                .containsEntry("target_type", "USER")
                .containsEntry("reason", "운영 정책 위반")
                .containsEntry("before_status", "ACTIVE")
                .containsEntry("after_status", "BANNED");
        assertThat(((Timestamp) savedAuditLog.get("occurred_at")).toInstant())
                .isBetween(beforeExecution, afterExecution);
    }

    @Test
    @DisplayName("업무 트랜잭션 없이 감사 로그만 저장할 수 없다")
    void createAuditLog_withoutBusinessTransaction_throwsIllegalTransactionStateException() {
        // Given
        UUID requestId = UUID.randomUUID();

        // When & Then
        assertThatThrownBy(() -> adminAuditLogService.createAuditLog(
                createCommand(ACTOR_ADMIN_ID, requestId)
        )).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(auditLogCount(requestId)).isZero();
    }

    @Test
    @DisplayName(
            "감사 INSERT 뒤 업무가 실패하면 "
                    + "변경과 감사 로그를 함께 롤백한다"
    )
    void createAuditLog_businessFailure_rollsBackChangeAndAuditLog() {
        // Given
        UUID requestId = UUID.randomUUID();

        // When & Then
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "UPDATE users SET status = 'BANNED' WHERE id = ?",
                    TARGET_USER_ID
            );
            adminAuditLogService.createAuditLog(createCommand(ACTOR_ADMIN_ID, requestId));
            entityManager.flush();
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "업무 변경에 실패했습니다."
            );
        }))
                .isInstanceOf(BusinessException.class)
                .hasMessage("업무 변경에 실패했습니다.");
        assertThat(userStatus()).isEqualTo("ACTIVE");
        assertThat(auditLogCount(requestId)).isZero();
    }

    @Test
    @DisplayName("감사 로그 저장이 실패하면 앞선 업무 변경도 함께 롤백한다")
    void createAuditLog_auditPersistenceFailure_rollsBackBusinessChange() {
        // Given
        UUID requestId = UUID.randomUUID();

        // When & Then
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "UPDATE users SET status = 'BANNED' WHERE id = ?",
                    TARGET_USER_ID
            );
            adminAuditLogService.createAuditLog(createCommand(UNKNOWN_ADMIN_ID, requestId));
            entityManager.flush();
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(userStatus()).isEqualTo("ACTIVE");
        assertThat(auditLogCount(requestId)).isZero();
    }

    private AdminAuditLogCommand createCommand(UUID actorAdminId, UUID requestId) {
        return new AdminAuditLogCommand(
                actorAdminId,
                AdminAction.USER_BANNED,
                AdminTargetType.USER,
                TARGET_USER_ID,
                "운영 정책 위반",
                AdminAuditSnapshot.from(Map.of("status", "ACTIVE")),
                AdminAuditSnapshot.from(Map.of("status", "BANNED")),
                requestId
        );
    }

    private String userStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT CAST(status AS TEXT) FROM users WHERE id = ?",
                String.class,
                TARGET_USER_ID
        );
    }

    private int auditLogCount(UUID requestId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs WHERE request_id = ?",
                Integer.class,
                requestId
        );
    }

    private void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM admin_audit_logs WHERE actor_admin_id = ? OR target_id = ?",
                ACTOR_ADMIN_ID,
                TARGET_USER_ID
        );
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", TARGET_USER_ID);
        jdbcTemplate.update("DELETE FROM admins WHERE id = ?", ACTOR_ADMIN_ID);
    }
}
