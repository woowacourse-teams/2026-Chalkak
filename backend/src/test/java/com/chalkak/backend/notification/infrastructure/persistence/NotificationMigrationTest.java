package com.chalkak.backend.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class NotificationMigrationTest extends IntegrationTestSupport {

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-06T00:00:00Z");

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("기존 승인·반려 대상과 사유를 이관하면서 알림 ID·사건·읽음·시각을 보존한다")
    void migrate_existingNotifications_preservesDataAndValidatesGenericSources() throws Exception {
        // Given
        String schema = "notification_migration_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate admin = new JdbcTemplate(dataSource);
        admin.execute("CREATE SCHEMA " + schema);
        try {
            migrate(schema, "202609301400");
            try (Connection connection = dataSource.getConnection()) {
                String previousSchema = connection.getSchema();
                connection.setSchema(schema);
                try {
                    JdbcTemplate jdbc = new JdbcTemplate(
                            new SingleConnectionDataSource(connection, true));
                    UUID userId = insertFixtures(jdbc);
                    List<Map<String, Object>> before = jdbc.queryForList("""
                            SELECT id, user_id, post_id, event_key, type, title, body,
                                rejection_reason, read_at, created_at
                            FROM notifications ORDER BY id
                            """);

                    // When
                    migrate(schema, "202610061630");

                    // Then
                    assertThat(jdbc.queryForList(
                            """
                                    SELECT id, user_id, source_id AS post_id, event_key, type, title, body,
                                        payload ->> 'rejectionReason' AS rejection_reason, read_at, created_at
                                    FROM notifications ORDER BY id
                                    """))
                            .isEqualTo(before);
                    assertThat(jdbc.queryForObject("""
                            SELECT COUNT(*) FROM notifications WHERE source_type = 'POST'
                            """, Integer.class)).isEqualTo(2);
                    assertThat(jdbc.queryForObject("""
                            SELECT COUNT(*) FROM information_schema.columns
                            WHERE table_schema = ? AND table_name = 'notifications'
                                AND column_name IN ('post_id', 'rejection_reason')
                            """, Integer.class, schema)).isZero();
                    assertThat(jdbc.queryForObject(
                            """
                                    SELECT COUNT(*) FROM information_schema.table_constraints
                                    WHERE table_schema = ? AND table_name = 'notifications'
                                        AND constraint_name IN ('fk_notifications_post', 'fk_notifications_event')
                                    """,
                            Integer.class, schema)).isZero();

                    assertSourceAndPayloadConstraints(jdbc, userId);
                    assertDuplicateEventRejected(jdbc, before.getFirst());
                } finally {
                    connection.setSchema(previousSchema);
                }
            }
        } finally {
            admin.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private void assertSourceAndPayloadConstraints(JdbcTemplate jdbc, UUID userId) {
        // 스키마 전용 가상 종류로 게시물 없는 알림을 허용하는지 확인한다. 운영 종류를 추가하지 않는다.
        insertNotification(jdbc, userId, "SCHEMA_TEST", null, null, null);
        insertNotification(jdbc, userId, "SCHEMA_TEST", "POST", UUID.randomUUID(), "{}");
        assertThatThrownBy(() -> insertNotification(
                jdbc, userId, "SCHEMA_TEST", "POST", null, null))
                .hasMessageContaining("ck_notifications_source_pair");
        assertThatThrownBy(() -> insertNotification(
                jdbc, userId, "SCHEMA_TEST", null, UUID.randomUUID(), null))
                .hasMessageContaining("ck_notifications_source_pair");
        assertThatThrownBy(() -> insertNotification(
                jdbc, userId, "POST_APPROVED", null, null, null))
                .hasMessageContaining("ck_notifications_moderation_source");
        assertThatThrownBy(() -> insertNotification(
                jdbc, userId, "SCHEMA_TEST", null, null, "[]"))
                .hasMessageContaining("ck_notifications_payload_object");
        for (String payload : new String[]{null, "{}", "{\"rejectionReason\":null}",
                "{\"rejectionReason\":1}", "{\"rejectionReason\":\" \"}",
                "{\"rejectionReason\":\"" + "가".repeat(501) + "\"}"}) {
            assertThatThrownBy(() -> insertNotification(
                    jdbc, userId, "POST_REJECTED", "POST", UUID.randomUUID(), payload))
                    .hasMessageContaining("ck_notifications_rejection_reason");
        }
        insertNotification(jdbc, userId, "POST_REJECTED", "POST", UUID.randomUUID(),
                "{\"rejectionReason\":\"" + "가".repeat(500) + "\"}");
        assertThatThrownBy(() -> insertNotification(jdbc, userId, "POST_APPROVED", "POST",
                UUID.randomUUID(), "{\"rejectionReason\":\"사유\"}"))
                .hasMessageContaining("ck_notifications_rejection_reason");
    }

    private void assertDuplicateEventRejected(JdbcTemplate jdbc, Map<String, Object> original) {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO notifications (user_id, event_key, type, title, body,
                    source_type, source_id, payload, created_at)
                SELECT user_id, event_key, type, title, body,
                    source_type, source_id, payload, created_at
                FROM notifications WHERE id = ?
                """, original.get("id")))
                .hasMessageContaining("ux_notifications_user_event");
    }

    private void insertNotification(JdbcTemplate jdbc, UUID userId, String type,
            String sourceType, UUID sourceId, String payload) {
        jdbc.update("""
                INSERT INTO notifications (user_id, event_key, type, title, body,
                    source_type, source_id, payload, created_at)
                VALUES (?, ?, ?, '테스트', '테스트', ?, ?, CAST(? AS jsonb), ?)
                """, userId, UUID.randomUUID(), type, sourceType, sourceId, payload,
                Timestamp.from(OCCURRED_AT));
    }

    private void migrate(String schema, String target) {
        Flyway.configure().dataSource(dataSource).defaultSchema(schema).schemas(schema)
                .locations("classpath:db/migration").target(target).load().migrate();
    }

    private UUID insertFixtures(JdbcTemplate jdbc) {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, email, signature_original_storage_key)
                VALUES (?, 'notification-migration@test.chalkak', 'signatures/test')
                """, userId);
        jdbc.update(
                "INSERT INTO admins (id, username, password) VALUES (?, 'migration-admin', 'test')",
                adminId);
        jdbc.update(
                """
                        INSERT INTO topics (id, title, topic_date, starts_at, ends_at)
                        VALUES (?, '이관 주제', CURRENT_DATE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 day')
                        """,
                topicId);
        jdbc.update("INSERT INTO photos (id, original_storage_key) VALUES (?, 'posts/migration')",
                photoId);
        jdbc.update("""
                INSERT INTO posts (id, user_id, topic_id, photo_id, title)
                VALUES (?, ?, ?, ?, '이관 게시물')
                """, postId, userId, topicId, photoId);
        for (String type : List.of("POST_APPROVED", "POST_REJECTED")) {
            UUID eventKey = UUID.randomUUID();
            jdbc.update(
                    """
                            INSERT INTO admin_audit_logs (id, actor_admin_id, action, target_type,
                                target_id, reason, before_state, after_state, occurred_at, request_id)
                            VALUES (?, ?, CAST(? AS admin_action), CAST('POST' AS admin_target_type), ?,
                                '이관 검수', '{"status":"PENDING"}'::jsonb, '{"status":"REVIEWED"}'::jsonb, ?, ?)
                            """,
                    eventKey, adminId, type, postId, Timestamp.from(OCCURRED_AT),
                    UUID.randomUUID());
            String reason = type.equals("POST_REJECTED") ? "원문 \"사진\"\n품질" : null;
            jdbc.update("""
                    INSERT INTO notifications (user_id, post_id, event_key, type, title, body,
                        rejection_reason, read_at, created_at)
                    VALUES (?, ?, ?, ?, '기존 제목', '기존 본문', ?, ?, ?)
                    """, userId, postId, eventKey, type, reason,
                    reason == null ? Timestamp.from(OCCURRED_AT.plusSeconds(1)) : null,
                    Timestamp.from(OCCURRED_AT));
        }
        return userId;
    }
}
