package com.chalkak.backend.like.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Connection;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class PostLikeMilestoneMigrationTest extends IntegrationTestSupport {

    @Autowired
    private DataSource dataSource;

    private String schema;
    private String previousSchema;
    private Connection connection;
    private JdbcTemplate jdbc;
    private UUID userId;
    private UUID topicId;

    @BeforeEach
    void setUp() throws Exception {
        schema = "like_milestone_migration_" + UUID.randomUUID().toString().replace("-", "");
        migrate("202611021701");
        connection = dataSource.getConnection();
        previousSchema = connection.getSchema();
        connection.setSchema(schema);
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        userId = UUID.randomUUID();
        topicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users (id, email, signature_original_storage_key) VALUES (?, 'migration-like@example.com', 'signature/test')",
                userId);
        jdbc.update(
                "INSERT INTO topics (id, title, topic_date, starts_at, ends_at) VALUES (?, '마일스톤 초기화', CURRENT_DATE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 day')",
                topicId);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null) {
            connection.setSchema(previousSchema);
            connection.close();
        }
        new JdbcTemplate(dataSource).execute("DROP SCHEMA " + schema + " CASCADE");
    }

    @Test
    @DisplayName("기존 개수의 최고 단계만 초기화하고 알림은 만들지 않으며 신규 행은 0으로 시작한다")
    void migrate_existingCounts_initializesBaselineWithoutNotifications() {
        // Given
        UUID three = insertPost(3L);
        UUID eight = insertPost(8L);
        UUID seventeen = insertPost(17L);
        UUID hundred = insertPost(100L);

        // When
        migrate("202611031600");

        // Then
        assertThat(progress(three)).isZero();
        assertThat(progress(eight)).isEqualTo(5L);
        assertThat(progress(seventeen)).isEqualTo(15L);
        assertThat(progress(hundred)).isEqualTo(100L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT to_regclass('post_like_push_milestones') IS NULL",
                Boolean.class)).isTrue();
        UUID fresh = insertPost(0L);
        assertThat(progress(fresh)).isZero();
        for (long invalid : new long[]{-5L, 1L, 4L, 6L}) {
            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE post_like_counts SET last_milestone_count = ? WHERE post_id = ?",
                    invalid,
                    fresh
            )).hasMessageContaining("ck_post_like_counts_milestone");
        }
        jdbc.update("UPDATE post_like_counts SET last_milestone_count = 10 WHERE post_id = ?",
                fresh);
        assertThat(progress(fresh)).isEqualTo(10L);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO notifications (user_id, event_key, type, title, body, sqs_publish_status, created_at) VALUES (?, ?, 'POST_LIKE_MILESTONE', '제목', '본문', 'NOT_REQUIRED', CURRENT_TIMESTAMP)",
                userId,
                UUID.randomUUID()
        )).hasMessageContaining("ck_notifications_like_milestone_source");
    }

    private long progress(UUID postId) {
        return jdbc.queryForObject(
                "SELECT last_milestone_count FROM post_like_counts WHERE post_id = ?",
                Long.class,
                postId
        );
    }

    private UUID insertPost(long count) {
        UUID photoId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO photos (id, original_storage_key) VALUES (?, ?)",
                photoId,
                "post/" + photoId
        );
        jdbc.update(
                "INSERT INTO posts (id, user_id, topic_id, photo_id, moderation_status) VALUES (?, ?, ?, ?, 'REJECTED')",
                postId,
                userId,
                topicId,
                photoId
        );
        jdbc.update(
                "INSERT INTO post_like_counts (post_id, like_count) VALUES (?, ?)",
                postId,
                count
        );
        return postId;
    }

    private void migrate(String target) {
        Flyway.configure().dataSource(dataSource).defaultSchema(schema).schemas(schema)
                .locations("classpath:db/migration").target(target).load().migrate();
    }
}
