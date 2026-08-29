package com.chalkak.backend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.chalkak.backend.exception.BaseException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.UserStatus;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AdminUserStatusConcurrencyTest extends IntegrationTestSupport {

    private static final String SUCCESS = "SUCCESS";
    private static final UUID FIRST_ADMIN_ID =
            UUID.fromString("0198fd11-0000-7000-8000-000000000001");
    private static final UUID SECOND_ADMIN_ID =
            UUID.fromString("0198fd11-0000-7000-8000-000000000002");
    private static final UUID UNKNOWN_ADMIN_ID =
            UUID.fromString("0198fd11-0000-7000-8000-000000000099");
    private static final UUID USER_ID =
            UUID.fromString("0198fd11-0000-7000-8000-000000000003");

    @Autowired
    private AdminUserStatusService adminUserStatusService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertAdmin(FIRST_ADMIN_ID, "first-user-manager");
        insertAdmin(SECOND_ADMIN_ID, "second-user-manager");
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'concurrent-status@example.com', 'ACTIVE',
                    'signatures/concurrent-status', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, USER_ID);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("동시에 같은 사용자를 차단하면 한 요청만 성공하고 감사 로그도 하나만 남긴다")
    void updateStatus_concurrentBan_changesExactlyOnce() throws Exception {
        // Given
        Callable<AdminUserStatusResult> first = () -> adminUserStatusService.updateStatus(
                USER_ID, FIRST_ADMIN_ID, UserStatus.BANNED, "첫 번째 차단 요청");
        Callable<AdminUserStatusResult> second = () -> adminUserStatusService.updateStatus(
                USER_ID, SECOND_ADMIN_ID, UserStatus.BANNED, "두 번째 차단 요청");

        // When
        List<StatusAttempt> attempts = runConcurrently(first, second);

        // Then
        assertThat(attempts)
                .extracting(StatusAttempt::outcome)
                .containsExactlyInAnyOrder(SUCCESS, ErrorCode.RESOURCE_STATE_CHANGED.name());
        assertThat(findUserStatus()).isEqualTo("BANNED");
        assertThat(countAuditLogs()).isEqualTo(1);
    }

    @Test
    @DisplayName("감사 로그의 관리자 외래키 저장이 실패하면 사용자 차단도 롤백한다")
    void updateStatus_auditActorForeignKeyFailure_rollsBackUserTransition() {
        // Given
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        // When
        Throwable exception = catchThrowable(() -> transactionTemplate.executeWithoutResult(
                status -> adminUserStatusService.updateStatus(
                        USER_ID,
                        UNKNOWN_ADMIN_ID,
                        UserStatus.BANNED,
                        "외래키 실패 검증"
                )
        ));

        // Then
        assertThat(exception).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(findUserStatus()).isEqualTo("ACTIVE");
        assertThat(countAuditLogs()).isZero();
    }

    private List<StatusAttempt> runConcurrently(
            Callable<AdminUserStatusResult> first,
            Callable<AdminUserStatusResult> second
    ) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<StatusAttempt> firstResult = executor.submit(() -> attempt(barrier, first));
            Future<StatusAttempt> secondResult = executor.submit(() -> attempt(barrier, second));
            return List.of(
                    firstResult.get(10, TimeUnit.SECONDS),
                    secondResult.get(10, TimeUnit.SECONDS)
            );
        }
    }

    private StatusAttempt attempt(
            CyclicBarrier barrier,
            Callable<AdminUserStatusResult> action
    ) {
        try {
            barrier.await();
            return new StatusAttempt(SUCCESS, action.call());
        } catch (BaseException exception) {
            return new StatusAttempt(exception.getErrorCode().name(), null);
        } catch (Exception exception) {
            return new StatusAttempt(
                    "UNEXPECTED:" + exception.getClass().getSimpleName(),
                    null
            );
        }
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

    private void insertAdmin(UUID id, String username) {
        jdbcTemplate.update("""
                INSERT INTO admins (id, username, password, created_at, updated_at)
                VALUES (?, ?, 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, username);
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM admin_audit_logs WHERE target_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", USER_ID);
        jdbcTemplate.update(
                "DELETE FROM admins WHERE id IN (?, ?, ?)",
                FIRST_ADMIN_ID,
                SECOND_ADMIN_ID,
                UNKNOWN_ADMIN_ID
        );
    }

    private record StatusAttempt(
            String outcome,
            AdminUserStatusResult result
    ) {
    }
}
