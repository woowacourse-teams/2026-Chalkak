package com.chalkak.backend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminPostModerationServiceTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6571f1");
    private static final UUID USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6571a1");
    private static final UUID TOPIC_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6571b1");
    private static final UUID PHOTO_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6571c1");
    private static final UUID POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6571d1");

    @Autowired
    private AdminPostModerationService adminPostModerationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        insertAdmin();
        insertUser();
        insertTopic();
        insertPhoto();
    }

    @Test
    @DisplayName("대기 중인 게시물을 승인하면 상태와 처리 시각 및 감사 로그를 원자적으로 저장한다")
    void moderate_approvedRequest_updatesPostAndCreatesAuditLog() {
        // Given
        insertPost(ModerationStatus.PENDING, null);

        // When
        AdminPostModerationResult result = adminPostModerationService.moderate(
                POST_ID,
                ADMIN_ID,
                ModerationStatus.APPROVED,
                null
        );
        entityManager.flush();
        entityManager.clear();

        // Then
        PostModerationRow post = findPost();
        ModerationAuditRow audit = findSingleAuditLog();
        assertThat(result.postId()).isEqualTo(POST_ID);
        assertThat(result.moderationStatus()).isEqualTo(ModerationStatus.APPROVED);
        assertThat(result.moderatedBy()).isEqualTo(ADMIN_ID);
        assertThat(result.moderatedAt()).isNotNull();
        assertThat(result.rejectionReason()).isNull();
        assertThat(post.moderationStatus()).isEqualTo("APPROVED");
        assertThat(post.moderatedAt()).isCloseTo(
                result.moderatedAt(),
                within(1, ChronoUnit.MICROS)
        );
        assertModerationAudit(
                audit,
                "POST_APPROVED",
                "APPROVED",
                result.moderatedAt(),
                null
        );
    }

    @Test
    @DisplayName("대기 중인 게시물을 거절하면 정규화한 사유와 처리자를 감사 로그에 저장한다")
    void moderate_rejectedRequest_updatesPostAndCreatesAuditLogWithReason() {
        // Given
        insertPost(ModerationStatus.PENDING, null);

        // When
        AdminPostModerationResult result = adminPostModerationService.moderate(
                POST_ID,
                ADMIN_ID,
                ModerationStatus.REJECTED,
                "  운영 정책 위반  "
        );
        entityManager.flush();
        entityManager.clear();

        // Then
        PostModerationRow post = findPost();
        ModerationAuditRow audit = findSingleAuditLog();
        assertThat(result.postId()).isEqualTo(POST_ID);
        assertThat(result.moderationStatus()).isEqualTo(ModerationStatus.REJECTED);
        assertThat(result.moderatedBy()).isEqualTo(ADMIN_ID);
        assertThat(result.moderatedAt()).isNotNull();
        assertThat(result.rejectionReason()).isEqualTo("운영 정책 위반");
        assertThat(post.moderationStatus()).isEqualTo("REJECTED");
        assertThat(post.moderatedAt()).isCloseTo(
                result.moderatedAt(),
                within(1, ChronoUnit.MICROS)
        );
        assertModerationAudit(
                audit,
                "POST_REJECTED",
                "REJECTED",
                result.moderatedAt(),
                "운영 정책 위반"
        );
    }

    @Test
    @DisplayName("존재하지 않는 게시물은 검수할 수 없다")
    void moderate_unknownPost_throwsNotFoundException() {
        // When
        NotFoundException exception = catchThrowableOfType(
                () -> adminPostModerationService.moderate(
                        POST_ID,
                        ADMIN_ID,
                        ModerationStatus.APPROVED,
                        null
                ),
                NotFoundException.class
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(countAuditLogs()).isZero();
    }

    @ParameterizedTest
    @EnumSource(
            value = ModerationStatus.class,
            names = {"VALIDATING", "APPROVED", "REJECTED"}
    )
    @DisplayName("대기 상태가 아닌 게시물은 현재 상태 재조회 오류로 검수를 거부한다")
    void moderate_nonPendingPost_throwsResourceStateChangedException(
            ModerationStatus currentStatus
    ) {
        // Given
        Instant previousModeratedAt = currentStatus == ModerationStatus.VALIDATING
                ? null
                : Instant.parse("2026-08-28T03:30:00Z");
        insertPost(currentStatus, previousModeratedAt);

        // When
        BusinessException exception = catchThrowableOfType(
                () -> adminPostModerationService.moderate(
                        POST_ID,
                        ADMIN_ID,
                        ModerationStatus.APPROVED,
                        null
                ),
                BusinessException.class
        );

        // Then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_STATE_CHANGED);
        PostModerationRow post = findPost();
        assertThat(post.moderationStatus()).isEqualTo(currentStatus.name());
        assertThat(post.moderatedAt()).isEqualTo(previousModeratedAt);
        assertThat(countAuditLogs()).isZero();
    }

    @Test
    @DisplayName("삭제된 대기 게시물은 검수 대상에서 제외한다")
    void moderate_deletedPendingPost_throwsNotFoundException() {
        // Given
        insertPost(ModerationStatus.PENDING, null);
        jdbcTemplate.update(
                "UPDATE posts SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                POST_ID
        );

        // When
        NotFoundException exception = catchThrowableOfType(
                () -> adminPostModerationService.moderate(
                        POST_ID,
                        ADMIN_ID,
                        ModerationStatus.REJECTED,
                        "운영 정책 위반"
                ),
                NotFoundException.class
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(findPost().moderationStatus()).isEqualTo("PENDING");
        assertThat(countAuditLogs()).isZero();
    }

    private void assertModerationAudit(
            ModerationAuditRow audit,
            String expectedAction,
            String expectedAfterStatus,
            Instant moderatedAt,
            String expectedReason
    ) {
        assertThat(audit.action()).isEqualTo(expectedAction);
        assertThat(audit.actorAdminId()).isEqualTo(ADMIN_ID);
        assertThat(audit.targetType()).isEqualTo("POST");
        assertThat(audit.targetId()).isEqualTo(POST_ID);
        assertThat(audit.reason()).isEqualTo(expectedReason);
        assertThat(audit.beforeStatus()).isEqualTo("PENDING");
        assertThat(audit.beforeModeratedAt()).isNull();
        assertThat(audit.afterStatus()).isEqualTo(expectedAfterStatus);
        assertThat(audit.afterModeratedAt()).isEqualTo(moderatedAt.toString());
        assertThat(audit.afterModeratedBy()).isEqualTo(ADMIN_ID.toString());
        assertThat(audit.requestId()).isNotNull();
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
                SELECT CAST(action AS TEXT), actor_admin_id,
                       CAST(target_type AS TEXT), target_id, reason,
                       before_state ->> 'moderationStatus',
                       before_state ->> 'moderatedAt',
                       after_state ->> 'moderationStatus',
                       after_state ->> 'moderatedAt',
                       after_state ->> 'moderatedBy',
                       request_id
                FROM admin_audit_logs
                WHERE target_type = CAST('POST' AS admin_target_type)
                  AND target_id = ?
                """, (resultSet, rowNumber) -> new ModerationAuditRow(
                resultSet.getString(1),
                resultSet.getObject(2, UUID.class),
                resultSet.getString(3),
                resultSet.getObject(4, UUID.class),
                resultSet.getString(5),
                resultSet.getString(6),
                resultSet.getString(7),
                resultSet.getString(8),
                resultSet.getString(9),
                resultSet.getString(10),
                resultSet.getObject(11, UUID.class)
        ), POST_ID);
    }

    private int countAuditLogs() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs WHERE target_id = ?",
                Integer.class,
                POST_ID
        );
    }

    private void insertAdmin() {
        jdbcTemplate.update("""
                INSERT INTO admins (
                    id, username, password, created_at, updated_at
                ) VALUES (
                    ?, 'moderation-service-admin', 'test-password',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, ADMIN_ID);
    }

    private void insertUser() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    created_at, updated_at
                ) VALUES (
                    ?, 'moderation-service@example.com', CAST('ACTIVE' AS user_status),
                    'chalkak/signatures/moderation-service/original.webp',
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
                    ?, '관리자 검수 서비스', CURRENT_DATE,
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
                    ?, 'chalkak/posts/moderation-service/original.webp',
                    CAST('{}' AS jsonb), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, PHOTO_ID);
    }

    private void insertPost(
            ModerationStatus moderationStatus,
            Instant moderatedAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, title,
                    moderation_status, moderated_at, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, '검수 대상', CAST(? AS moderation_status), ?,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                POST_ID,
                USER_ID,
                TOPIC_ID,
                PHOTO_ID,
                moderationStatus.name(),
                timestamp(moderatedAt)
        );
    }

    private Timestamp timestamp(Instant instant) {
        if (instant == null) {
            return null;
        }
        return Timestamp.from(instant);
    }

    private Instant instant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant();
    }

    private record PostModerationRow(
            String moderationStatus,
            Instant moderatedAt
    ) {
    }

    private record ModerationAuditRow(
            String action,
            UUID actorAdminId,
            String targetType,
            UUID targetId,
            String reason,
            String beforeStatus,
            String beforeModeratedAt,
            String afterStatus,
            String afterModeratedAt,
            String afterModeratedBy,
            UUID requestId
    ) {
    }
}
