package com.chalkak.backend.admin.service.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.notification.service.NotificationService;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class AdminPostNotificationTransactionTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6572f1");
    private static final UUID USER_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6572a1");
    private static final UUID TOPIC_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6572b1");
    private static final UUID PHOTO_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6572c1");
    private static final UUID POST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6572d1");

    @Autowired
    private AdminPostCommandService adminPostCommandService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        clean();
        jdbcTemplate.update("""
                INSERT INTO admins (id, username, password, created_at, updated_at)
                VALUES (?, 'notification-rollback-admin', 'test-password',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ADMIN_ID);
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    created_at, updated_at
                ) VALUES (
                    ?, 'notification-rollback@example.com', CAST('ACTIVE' AS user_status),
                    'chalkak/signatures/notification-rollback/original.webp',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    ?, '검수 알림 롤백', CURRENT_DATE,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, TOPIC_ID);
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, metadata, created_at, updated_at
                ) VALUES (
                    ?, 'chalkak/posts/notification-rollback/original.webp',
                    CAST('{}' AS jsonb), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, PHOTO_ID);
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, title,
                    moderation_status, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, '검수 대상', CAST('PENDING' AS moderation_status),
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, POST_ID, USER_ID, TOPIC_ID, PHOTO_ID);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    @DisplayName("검수 트랜잭션이 롤백되면 게시물 변경과 감사 로그 및 알림이 모두 사라진다")
    void moderate_rolledBackTransaction_removesNotificationAndAudit() {
        // Given
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        // When
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            adminPostCommandService.moderate(
                    POST_ID,
                    ADMIN_ID,
                    ModerationStatus.APPROVED,
                    null);
            entityManager.flush();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT sqs_publish_status FROM notifications WHERE source_id = ?",
                    String.class, POST_ID)).isEqualTo("PENDING");
            throw new IllegalStateException("검수 트랜잭션 롤백");
        })).isInstanceOf(IllegalStateException.class);

        // Then
        assertThat(jdbcTemplate.queryForObject(
                "SELECT CAST(moderation_status AS TEXT) FROM posts WHERE id = ?",
                String.class,
                POST_ID)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs WHERE target_id = ?",
                Integer.class,
                POST_ID)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE source_type = 'POST' AND source_id = ?",
                Integer.class,
                POST_ID)).isZero();
    }

    @Test
    @DisplayName("검수가 커밋되면 게시물·감사 기록·알림의 발행 대기 상태가 함께 남는다")
    void moderate_committedTransaction_persistsPublicationWithPostAndAudit() {
        // When
        AdminPostModerationResult result = adminPostCommandService.moderate(
                POST_ID, ADMIN_ID, ModerationStatus.APPROVED, null);

        // Then
        assertThat(jdbcTemplate.queryForObject(
                "SELECT CAST(moderation_status AS TEXT) FROM posts WHERE id = ?",
                String.class, POST_ID)).isEqualTo("APPROVED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs WHERE target_id = ?",
                Integer.class, POST_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM notifications WHERE source_id = ?
                    AND sqs_publish_status = 'PENDING' AND next_attempt_at = created_at
                    AND sqs_published_at IS NULL
                """, Integer.class, POST_ID)).isEqualTo(1);
        Instant createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM notifications WHERE source_id = ?",
                (row, rowNum) -> row.getTimestamp(1).toInstant(), POST_ID);
        assertThat(createdAt).isCloseTo(result.moderatedAt(), within(1, ChronoUnit.MICROS));
    }

    @Test
    @DisplayName("알림 생성은 호출자의 업무 트랜잭션이 없으면 실행되지 않는다")
    void createForModeration_withoutBusinessTransaction_throwsIllegalTransactionStateException() {
        // Given
        Post post = entityManager.find(Post.class, POST_ID);

        // When & Then
        assertThatThrownBy(() -> notificationService.createForModeration(
                post, UUID.randomUUID(), ModerationStatus.APPROVED, null, Instant.now()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE source_type = 'POST' AND source_id = ?",
                Integer.class, POST_ID)).isZero();
    }

    private void clean() {
        new DatabaseCleaner(jdbcTemplate).clean();
        jdbcTemplate.update("DELETE FROM admin_audit_logs WHERE target_id = ?", POST_ID);
        jdbcTemplate.update("DELETE FROM admins WHERE id = ?", ADMIN_ID);
    }
}
