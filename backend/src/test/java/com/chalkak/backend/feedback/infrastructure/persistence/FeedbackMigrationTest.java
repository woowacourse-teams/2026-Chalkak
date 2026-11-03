package com.chalkak.backend.feedback.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Connection;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class FeedbackMigrationTest extends IntegrationTestSupport {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("기존 피드백은 원문을 보존해 삭제 표시하고 전환 후 문의는 표시하지 않는다")
    void migrate_existingFeedback_preservesContentAndSoftDeletesOnlyExistingRows()
            throws Exception {
        // Given: 운영 데이터가 아닌 테스트 DB의 독립 스키마에서 전환을 확인한다.
        String schema = "feedback_migration_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate admin = new JdbcTemplate(dataSource);
        admin.execute("CREATE SCHEMA " + schema);
        try {
            migrate(schema, "202611031600");
            try (Connection connection = dataSource.getConnection()) {
                String previousSchema = connection.getSchema();
                connection.setSchema(schema);
                try {
                    JdbcTemplate jdbc = new JdbcTemplate(
                            new SingleConnectionDataSource(connection, true));
                    UUID userId = UUID.randomUUID();
                    jdbc.update(
                            "INSERT INTO users (id, email, signature_original_storage_key) VALUES (?, ?, ?)",
                            userId,
                            userId + "@migration.test",
                            "signatures/" + userId
                    );
                    UUID oldFeedbackId = insertFeedback(jdbc, userId);
                    Map<String, Object> original = jdbc.queryForMap(
                            "SELECT id, user_id, content, created_at FROM feedbacks WHERE id = ?",
                            oldFeedbackId
                    );

                    // When
                    migrate(schema, "202611031610");
                    UUID newFeedbackId = insertFeedback(jdbc, userId);
                    migrate(schema, "202611031610");

                    // Then: 재시작 시 마이그레이션을 다시 확인해도 신규 문의를 삭제 표시하지 않는다.
                    assertThat(jdbc.queryForMap(
                            "SELECT id, user_id, content, created_at FROM feedbacks WHERE id = ?",
                            oldFeedbackId
                    )).isEqualTo(original);
                    assertThat(jdbc.queryForObject(
                            "SELECT deleted_at IS NOT NULL FROM feedbacks WHERE id = ?",
                            Boolean.class,
                            oldFeedbackId
                    )).isTrue();
                    assertThat(jdbc.queryForObject(
                            "SELECT id FROM feedbacks WHERE user_id = ? AND deleted_at IS NULL",
                            UUID.class,
                            userId
                    )).isEqualTo(newFeedbackId);
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feedbacks", Long.class))
                            .isEqualTo(2L);
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

    private UUID insertFeedback(JdbcTemplate jdbc, UUID userId) {
        UUID feedbackId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO feedbacks (id, user_id, content) VALUES (?, ?, '문의 원문')",
                feedbackId,
                userId
        );
        return feedbackId;
    }
}
