package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class NotificationCleanupSchedulerTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-29T00:00:00Z");
    private static final Instant NORMAL_THRESHOLD = NOW.minus(Duration.ofDays(60));
    private static final Instant WITHDRAWN_THRESHOLD = NOW.minus(Duration.ofDays(30));

    private final UUID adminId = UUID.randomUUID();
    private final UUID activeUserId = UUID.randomUUID();
    private final UUID withdrawnUserId = UUID.randomUUID();
    private final UUID topicId = UUID.randomUUID();
    private final UUID activePostId = UUID.randomUUID();
    private final UUID withdrawnPostId = UUID.randomUUID();

    @Autowired
    private NotificationCleanupScheduler notificationCleanupScheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Clock clock;

    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() {
        databaseCleaner = new DatabaseCleaner(jdbcTemplate);
        jdbcTemplate.execute("TRUNCATE TABLE admins RESTART IDENTITY CASCADE");
        databaseCleaner.clean();
        given(clock.instant()).willReturn(NOW);
        insertFixtures();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("TRUNCATE TABLE admins RESTART IDENTITY CASCADE");
        databaseCleaner.clean();
    }

    @Test
    @DisplayName("일반 회원의 알림은 생성 후 60일이 지나면 삭제한다")
    void deleteExpiredNotifications_activeUser_deletesOnlyOlderThanSixtyDays() {
        UUID expired = insertNotification(activeUserId, activePostId,
                NORMAL_THRESHOLD.minusSeconds(1));
        UUID onBoundary = insertNotification(activeUserId, activePostId, NORMAL_THRESHOLD);
        UUID recent = insertNotification(activeUserId, activePostId,
                NORMAL_THRESHOLD.plusSeconds(1));

        notificationCleanupScheduler.deleteExpiredNotifications();

        assertThat(exists(expired)).isFalse();
        assertThat(exists(onBoundary)).isTrue();
        assertThat(exists(recent)).isTrue();
    }

    @Test
    @DisplayName("탈퇴한 지 30일이 지나면 최근에 생성된 알림도 삭제한다")
    void deleteExpiredNotifications_oldWithdrawal_deletesRecentNotification() {
        withdrawAt(WITHDRAWN_THRESHOLD.minusSeconds(1));
        UUID notificationId = insertNotification(withdrawnUserId, withdrawnPostId,
                NOW.minus(Duration.ofDays(1)));

        notificationCleanupScheduler.deleteExpiredNotifications();

        assertThat(exists(notificationId)).isFalse();
    }

    @Test
    @DisplayName("탈퇴 30일 경계에 있는 회원의 알림은 생성 후 60일이 지나도 보관한다")
    void deleteExpiredNotifications_recentWithdrawal_keepsOlderNotification() {
        withdrawAt(WITHDRAWN_THRESHOLD);
        UUID notificationId = insertNotification(withdrawnUserId, withdrawnPostId,
                NORMAL_THRESHOLD.minusSeconds(1));

        notificationCleanupScheduler.deleteExpiredNotifications();

        assertThat(exists(notificationId)).isTrue();
    }

    private void withdrawAt(Instant withdrawnAt) {
        jdbcTemplate.update("UPDATE users SET deleted_at = ? WHERE id = ?",
                Timestamp.from(withdrawnAt), withdrawnUserId);
    }

    private UUID insertNotification(UUID userId, UUID postId, Instant createdAt) {
        UUID eventId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO admin_audit_logs (
                    id, actor_admin_id, action, target_type, target_id,
                    before_state, after_state, occurred_at, request_id
                ) VALUES (
                    ?, ?, CAST('POST_APPROVED' AS admin_action),
                    CAST('POST' AS admin_target_type), ?,
                    CAST('{"status":"PENDING"}' AS jsonb),
                    CAST('{"status":"APPROVED"}' AS jsonb), ?, ?
                )
                """, eventId, adminId, postId, Timestamp.from(createdAt), UUID.randomUUID());
        jdbcTemplate.update("""
                INSERT INTO notifications (
                    id, user_id, post_id, event_key, type, title, body, created_at
                ) VALUES (?, ?, ?, ?, 'POST_APPROVED', '승인', '피드에 공개', ?)
                """, notificationId, userId, postId, eventId, Timestamp.from(createdAt));
        return notificationId;
    }

    private boolean exists(UUID notificationId) {
        return jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM notifications WHERE id = ?)",
                Boolean.class,
                notificationId);
    }

    private void insertFixtures() {
        jdbcTemplate.update("""
                INSERT INTO admins (id, username, password, created_at, updated_at)
                VALUES (?, ?, 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, adminId, "notification-cleanup-" + adminId);
        insertUser(activeUserId);
        insertUser(withdrawnUserId);
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    ?, '알림 보관', CURRENT_DATE,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, topicId);
        insertPost(activeUserId, activePostId);
        insertPost(withdrawnUserId, withdrawnPostId);
    }

    private void insertUser(UUID userId) {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    created_at, updated_at
                ) VALUES (
                    ?, ?, CAST('ACTIVE' AS user_status), ?,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, userId, userId + "@example.com", "chalkak/signatures/" + userId);
    }

    private void insertPost(UUID userId, UUID postId) {
        UUID photoId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, metadata, created_at, updated_at
                ) VALUES (?, ?, CAST('{}' AS jsonb), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, photoId, "chalkak/posts/" + postId);
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, title,
                    moderation_status, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, '검수 완료', CAST('APPROVED' AS moderation_status),
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, postId, userId, topicId, photoId);
    }
}
