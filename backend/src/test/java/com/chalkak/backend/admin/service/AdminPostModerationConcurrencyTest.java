package com.chalkak.backend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.chalkak.backend.exception.BaseException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

/** 같은 게시물을 실제 별도 트랜잭션에서 검수해 PostgreSQL 행 잠금의 경합 결과를 검증한다. */
class AdminPostModerationConcurrencyTest extends IntegrationTestSupport {

    private static final String SUCCESS = "SUCCESS";
    private static final UUID APPROVING_ADMIN_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6572f1");
    private static final UUID REJECTING_ADMIN_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6572f2");
    private static final UUID UNKNOWN_ADMIN_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6572ff");
    private static final UUID USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6572a1");
    private static final UUID TOPIC_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6572b1");
    private static final UUID PHOTO_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6572c1");
    private static final UUID POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6572d1");
    private static final String REJECTION_REASON = "운영 정책 위반";

    @Autowired
    private AdminPostModerationService adminPostModerationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertAdmin(APPROVING_ADMIN_ID, "moderation-approver");
        insertAdmin(REJECTING_ADMIN_ID, "moderation-rejector");
        insertUser();
        insertTopic();
        insertPhoto();
        insertPendingPost();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("두 관리자가 동시에 승인과 거절을 요청하면 하나만 확정하고 감사 로그도 하나만 남긴다")
    void moderate_concurrentApproveAndReject_decidesExactlyOnce() throws Exception {
        // Given
        Callable<AdminPostModerationResult> approve = () ->
                adminPostModerationService.moderate(
                        POST_ID,
                        APPROVING_ADMIN_ID,
                        ModerationStatus.APPROVED,
                        null
                );
        Callable<AdminPostModerationResult> reject = () ->
                adminPostModerationService.moderate(
                        POST_ID,
                        REJECTING_ADMIN_ID,
                        ModerationStatus.REJECTED,
                        REJECTION_REASON
                );

        // When
        List<ModerationAttempt> attempts = runConcurrently(approve, reject);

        // Then
        assertThat(attempts)
                .extracting(ModerationAttempt::outcome)
                .containsExactlyInAnyOrder(
                        SUCCESS,
                        ErrorCode.RESOURCE_STATE_CHANGED.name()
                );
        AdminPostModerationResult decided = attempts.stream()
                .filter(attempt -> attempt.result() != null)
                .map(ModerationAttempt::result)
                .findFirst()
                .orElseThrow();
        PostModerationRow post = findPost();
        ModerationAuditRow audit = findSingleAuditLog();
        assertThat(post.moderationStatus()).isEqualTo(decided.moderationStatus().name());
        assertThat(post.moderatedAt()).isEqualTo(
                decided.moderatedAt().truncatedTo(ChronoUnit.MICROS)
        );
        assertThat(countAuditLogs()).isEqualTo(1);
        assertThat(audit.action()).isEqualTo(
                decided.moderationStatus() == ModerationStatus.APPROVED
                        ? "POST_APPROVED"
                        : "POST_REJECTED"
        );
        assertThat(audit.actorAdminId()).isEqualTo(decided.moderatedBy());
        assertThat(audit.reason()).isEqualTo(decided.rejectionReason());
        assertThat(audit.beforeStatus()).isEqualTo("PENDING");
        assertThat(audit.beforeModeratedAt()).isNull();
        assertThat(audit.afterStatus()).isEqualTo(decided.moderationStatus().name());
        assertThat(audit.afterModeratedAt()).isEqualTo(decided.moderatedAt().toString());
        assertThat(audit.afterModeratedBy()).isEqualTo(decided.moderatedBy().toString());
    }

    @Test
    @DisplayName("감사 로그 작업자 외래키 저장이 실패하면 게시물 검수 상태도 함께 롤백한다")
    void moderate_auditActorForeignKeyFailure_rollsBackPostTransition() {
        // Given
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        // When
        Throwable exception = catchThrowable(() -> transactionTemplate.executeWithoutResult(
                status -> adminPostModerationService.moderate(
                        POST_ID,
                        UNKNOWN_ADMIN_ID,
                        ModerationStatus.APPROVED,
                        null
                )
        ));

        // Then
        assertThat(exception).isInstanceOf(DataIntegrityViolationException.class);
        PostModerationRow post = findPost();
        assertThat(post.moderationStatus()).isEqualTo("PENDING");
        assertThat(post.moderatedAt()).isNull();
        assertThat(countAuditLogs()).isZero();
    }

    private List<ModerationAttempt> runConcurrently(
            Callable<AdminPostModerationResult> first,
            Callable<AdminPostModerationResult> second
    ) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<ModerationAttempt> firstResult = executor.submit(
                    () -> attempt(barrier, first)
            );
            Future<ModerationAttempt> secondResult = executor.submit(
                    () -> attempt(barrier, second)
            );
            return List.of(
                    firstResult.get(10, TimeUnit.SECONDS),
                    secondResult.get(10, TimeUnit.SECONDS)
            );
        }
    }

    private ModerationAttempt attempt(
            CyclicBarrier barrier,
            Callable<AdminPostModerationResult> action
    ) {
        try {
            barrier.await();
            return new ModerationAttempt(SUCCESS, action.call());
        } catch (BaseException exception) {
            return new ModerationAttempt(exception.getErrorCode().name(), null);
        } catch (Exception exception) {
            return new ModerationAttempt(
                    "UNEXPECTED:" + exception.getClass().getSimpleName(),
                    null
            );
        }
    }

    private PostModerationRow findPost() {
        return jdbcTemplate.queryForObject("""
                SELECT CAST(moderation_status AS TEXT), moderated_at
                FROM posts
                WHERE id = ?
                """, (resultSet, rowNumber) -> new PostModerationRow(
                resultSet.getString(1),
                instant(resultSet.getTimestamp(2))
        ), POST_ID);
    }

    private ModerationAuditRow findSingleAuditLog() {
        return jdbcTemplate.queryForObject("""
                SELECT CAST(action AS TEXT), actor_admin_id, reason,
                       before_state ->> 'moderationStatus',
                       before_state ->> 'moderatedAt',
                       after_state ->> 'moderationStatus',
                       after_state ->> 'moderatedAt',
                       after_state ->> 'moderatedBy'
                FROM admin_audit_logs
                WHERE target_type = CAST('POST' AS admin_target_type)
                  AND target_id = ?
                """, (resultSet, rowNumber) -> new ModerationAuditRow(
                resultSet.getString(1),
                resultSet.getObject(2, UUID.class),
                resultSet.getString(3),
                resultSet.getString(4),
                resultSet.getString(5),
                resultSet.getString(6),
                resultSet.getString(7),
                resultSet.getString(8)
        ), POST_ID);
    }

    private int countAuditLogs() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs WHERE target_id = ?",
                Integer.class,
                POST_ID
        );
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM admin_audit_logs WHERE target_id = ?", POST_ID);
        jdbcTemplate.update("DELETE FROM posts WHERE id = ?", POST_ID);
        jdbcTemplate.update("DELETE FROM photos WHERE id = ?", PHOTO_ID);
        jdbcTemplate.update("DELETE FROM topics WHERE id = ?", TOPIC_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", USER_ID);
        jdbcTemplate.update(
                "DELETE FROM admins WHERE id IN (?, ?, ?)",
                APPROVING_ADMIN_ID,
                REJECTING_ADMIN_ID,
                UNKNOWN_ADMIN_ID
        );
    }

    private void insertAdmin(UUID adminId, String username) {
        jdbcTemplate.update("""
                INSERT INTO admins (
                    id, username, password, created_at, updated_at
                ) VALUES (
                    ?, ?, 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, adminId, username);
    }

    private void insertUser() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    created_at, updated_at
                ) VALUES (
                    ?, 'moderation-concurrency@example.com',
                    CAST('ACTIVE' AS user_status),
                    'chalkak/signatures/moderation-concurrency/original.webp',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, USER_ID);
    }

    private void insertTopic() {
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at,
                    created_at, updated_at
                ) VALUES (
                    ?, '관리자 동시 검수', CURRENT_DATE,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, TOPIC_ID);
    }

    private void insertPhoto() {
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, metadata, created_at, updated_at
                ) VALUES (
                    ?, 'chalkak/posts/moderation-concurrency/original.webp',
                    CAST('{}' AS jsonb), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, PHOTO_ID);
    }

    private void insertPendingPost() {
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, title,
                    moderation_status, moderated_at, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, '동시 검수', CAST('PENDING' AS moderation_status), NULL,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, POST_ID, USER_ID, TOPIC_ID, PHOTO_ID);
    }

    private Instant instant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant();
    }

    private record ModerationAttempt(
            String outcome,
            AdminPostModerationResult result
    ) {
    }

    private record PostModerationRow(
            String moderationStatus,
            Instant moderatedAt
    ) {
    }

    private record ModerationAuditRow(
            String action,
            UUID actorAdminId,
            String reason,
            String beforeStatus,
            String beforeModeratedAt,
            String afterStatus,
            String afterModeratedAt,
            String afterModeratedBy
    ) {
    }
}
