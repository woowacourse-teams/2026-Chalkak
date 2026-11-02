package com.chalkak.backend.like.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Connection;
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

class PostLikeCountMigrationTest extends IntegrationTestSupport {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("기존 관계를 보존하고 좋아요가 없는 게시물과 삭제된 게시물까지 집계 행을 만든다")
    void migrate_existingPosts_backfillsEveryPostWithoutChangingRelations() throws Exception {
        // Given: 실제 테스트 DB 안의 별도 스키마에서 이전 버전부터 전환한다.
        String schema = "like_count_migration_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate admin = new JdbcTemplate(dataSource);
        admin.execute("CREATE SCHEMA " + schema);
        try {
            migrate(schema, "202610291401");
            try (Connection connection = dataSource.getConnection()) {
                String previousSchema = connection.getSchema();
                connection.setSchema(schema);
                try {
                    JdbcTemplate jdbc = new JdbcTemplate(
                            new SingleConnectionDataSource(connection, true));
                    UUID topicId = UUID.randomUUID();
                    jdbc.update("""
                            INSERT INTO topics (id, title, topic_date, starts_at, ends_at)
                            VALUES (?, '이관 검증', '2026-08-12',
                                '2026-08-12T00:00:00Z', '2026-08-13T00:00:00Z')
                            """, topicId);
                    UUID likedPostId = insertPost(jdbc, topicId);
                    UUID emptyPostId = insertPost(jdbc, topicId);
                    UUID deletedPostId = insertPost(jdbc, topicId);
                    jdbc.update("UPDATE posts SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                            deletedPostId);
                    jdbc.update(
                            """
                                    INSERT INTO post_likes (post_id, user_id)
                                    SELECT ?, user_id FROM posts WHERE id IN (?, ?)
                                    """,
                            likedPostId,
                            emptyPostId,
                            deletedPostId
                    );
                    List<Map<String, Object>> relations = jdbc
                            .queryForList("SELECT * FROM post_likes ORDER BY id");

                    // When
                    migrate(schema, "202611021230");

                    // Then
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_like_counts",
                            Long.class))
                            .isEqualTo(3);
                    assertThat(storedCount(jdbc, likedPostId)).isEqualTo(2);
                    assertThat(storedCount(jdbc, emptyPostId)).isZero();
                    assertThat(storedCount(jdbc, deletedPostId)).isZero();
                    assertThat(jdbc.queryForList("SELECT * FROM post_likes ORDER BY id"))
                            .isEqualTo(relations);
                } finally {
                    connection.setSchema(previousSchema);
                }
            }
        } finally {
            admin.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private void migrate(String schema, String version) {
        Flyway.configure()
                .dataSource(dataSource)
                .defaultSchema(schema)
                .schemas(schema)
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private UUID insertPost(JdbcTemplate jdbc, UUID topicId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users (id, email, signature_original_storage_key) VALUES (?, ?, ?)",
                id,
                id + "@migration.test",
                "chalkak/test/signatures/" + id
        );
        jdbc.update(
                "INSERT INTO photos (id, original_storage_key) VALUES (?, ?)",
                id,
                "chalkak/test/posts/" + id
        );
        jdbc.update(
                """
                        INSERT INTO posts (id, user_id, photo_id, topic_id, moderation_status)
                        VALUES (?, ?, ?, ?, 'APPROVED')
                        """,
                id,
                id,
                id,
                topicId
        );
        return id;
    }

    private long storedCount(JdbcTemplate jdbc, UUID postId) {
        return jdbc.queryForObject(
                "SELECT like_count FROM post_like_counts WHERE post_id = ?",
                Long.class,
                postId
        );
    }
}
