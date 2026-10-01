package com.chalkak.backend.admin.service.post;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminPostModerationLogTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6573f1");
    private static final UUID USER_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6573a1");
    private static final UUID TOPIC_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6573b1");
    private static final UUID PHOTO_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6573c1");
    private static final UUID POST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6573d1");

    @Autowired
    private AdminPostCommandService adminPostCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private final Logger moderationLogger = (Logger) LoggerFactory.getLogger("chalkak.moderation");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO admins (id, username, password, created_at, updated_at)
                VALUES (?, 'moderation-log-admin', 'test-password',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ADMIN_ID);
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    created_at, updated_at
                ) VALUES (
                    ?, 'moderation-log-admin@example.com', CAST('ACTIVE' AS user_status),
                    'chalkak/signatures/moderation-log/original.webp',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    ?, '검수 결정 로그', CURRENT_DATE,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, TOPIC_ID);
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, metadata, created_at, updated_at
                ) VALUES (
                    ?, 'chalkak/posts/moderation-log/original.webp',
                    CAST('{}' AS jsonb), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, PHOTO_ID);
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, title,
                    moderation_status, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, '검수 대상', CAST('PENDING' AS moderation_status),
                    CURRENT_TIMESTAMP - INTERVAL '90 seconds', CURRENT_TIMESTAMP
                )
                """, POST_ID, USER_ID, TOPIC_ID, PHOTO_ID);
        appender.start();
        moderationLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        moderationLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("관리자가 승인하면 approved 로그에 대기 시간을 초 단위로 남긴다")
    void moderate_approve_logsApprovedWithWaitSeconds() {
        // When
        adminPostCommandService.moderate(POST_ID, ADMIN_ID, ModerationStatus.APPROVED, null);
        entityManager.flush();

        // Then
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(keyValue(event, "type")).isEqualTo("moderation");
        assertThat(keyValue(event, "event")).isEqualTo("approved");
        assertThat(keyValue(event, "postId")).isEqualTo(POST_ID);
        assertThat(keyValue(event, "waitSeconds")).isEqualTo(storedWaitSeconds());
    }

    @Test
    @DisplayName("관리자가 반려하면 rejected 로그를 한 건 남긴다")
    void moderate_reject_logsRejected() {
        // When
        adminPostCommandService.moderate(POST_ID, ADMIN_ID, ModerationStatus.REJECTED, "부적절한 사진");

        // Then
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(keyValue(event, "event")).isEqualTo("rejected");
        assertThat(keyValue(event, "postId")).isEqualTo(POST_ID);
    }

    private long storedWaitSeconds() {
        Instant createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM posts WHERE id = ?",
                Timestamp.class,
                POST_ID).toInstant();
        Instant moderatedAt = jdbcTemplate.queryForObject(
                "SELECT moderated_at FROM posts WHERE id = ?",
                Timestamp.class,
                POST_ID).toInstant();
        return Duration.between(createdAt, moderatedAt).getSeconds();
    }

    private Object keyValue(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream()
                .filter(pair -> pair.key.equals(key))
                .findFirst()
                .orElseThrow().value;
    }
}
