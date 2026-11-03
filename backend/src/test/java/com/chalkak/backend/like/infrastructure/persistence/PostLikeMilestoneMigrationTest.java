package com.chalkak.backend.like.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.support.IntegrationTestSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        migrate("202611032315");
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
    @DisplayName("마이그레이션은 기존 개수와 무관하게 최고 단계를 0으로 추가하고 제약을 적용한다")
    void migrate_existingCounts_addsZeroBaselineAndConstraints() {
        // Given
        UUID three = insertPost(3L);
        UUID eight = insertPost(8L);
        UUID seventeen = insertPost(17L);
        UUID hundred = insertPost(100L);

        // When
        migrate("202611032316");

        // Then
        assertThat(progress(three)).isZero();
        assertThat(progress(eight)).isZero();
        assertThat(progress(seventeen)).isZero();
        assertThat(progress(hundred)).isZero();
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

    @Test
    @DisplayName("별도 초기화 SQL은 현재 단계까지만 올리고 재실행해도 높은 단계와 알림을 변경하지 않는다")
    void initialize_existingCounts_advancesBaselineWithoutNotifications() throws Exception {
        // Given
        UUID eight = insertPost(8L);
        UUID seventeen = insertPost(17L);
        UUID decreased = insertPost(4L);
        migrate("202611032316");
        jdbc.update("UPDATE post_like_counts SET last_milestone_count = 10 WHERE post_id = ?",
                decreased);

        // When
        initializeBaselines();
        initializeBaselines();

        // Then
        assertThat(progress(eight)).isEqualTo(5L);
        assertThat(progress(seventeen)).isEqualTo(15L);
        assertThat(progress(decreased)).isEqualTo(10L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Long.class)).isZero();
    }

    @Test
    @DisplayName("이미 좋아요 알림이 존재하면 초기화를 거부하고 최고 단계를 유지한다")
    void initialize_existingLikeNotification_rejectsWithoutChanges() {
        // Given
        UUID postId = insertPost(17L);
        migrate("202611032316");
        jdbc.update(
                "INSERT INTO notifications (user_id, event_key, type, source_type, source_id, title, body, sqs_publish_status, created_at) VALUES (?, ?, 'POST_LIKE_MILESTONE', 'POST', ?, '제목', '본문', 'NOT_REQUIRED', CURRENT_TIMESTAMP)",
                userId,
                UUID.randomUUID(),
                postId
        );

        // When & Then
        assertThatThrownBy(this::initializeBaselines)
                .hasMessageContaining("Like notifications already exist");
        assertThat(progress(postId)).isZero();
    }

    @Test
    @DisplayName("초기화 SQL이 실패하면 전체 갱신을 롤백한다")
    void initialize_updateFailure_rollsBack() {
        // Given
        UUID eight = insertPost(8L);
        UUID seventeen = insertPost(17L);
        migrate("202611032316");
        jdbc.execute(
                "ALTER TABLE post_like_counts ADD CONSTRAINT ck_test_baseline_failure CHECK (last_milestone_count < 10)");

        // When & Then
        assertThatThrownBy(this::initializeBaselines)
                .hasMessageContaining("ck_test_baseline_failure");
        assertThat(progress(eight)).isZero();
        assertThat(progress(seventeen)).isZero();
    }

    private void initializeBaselines() throws IOException {
        String sql = Files.readString(Path.of("deploy/scripts/initialize_like_milestones.sql"));
        try {
            jdbc.execute(sql);
        } catch (RuntimeException exception) {
            jdbc.execute("ROLLBACK");
            throw exception;
        }
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
