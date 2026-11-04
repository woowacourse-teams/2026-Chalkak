package com.chalkak.backend.feedback.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.callback.BaseCallback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class FeedbackMigrationTest extends IntegrationTestSupport {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("문의 삭제 감사 마이그레이션은 FEEDBACK 대상을 허용하고 다른 대상은 거절한다")
    void migrate_feedbackDeletionAudit_rejectsMismatchedTarget() throws Exception {
        // Given: 테스트 DB의 독립 스키마에 실제 마이그레이션을 적용한다.
        String schema = "feedback_audit_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate admin = new JdbcTemplate(dataSource);
        admin.execute("CREATE SCHEMA " + schema);
        try {
            migrate(schema, "202611032101");
            try (Connection connection = dataSource.getConnection()) {
                String previousSchema = connection.getSchema();
                connection.setSchema(schema);
                try {
                    JdbcTemplate jdbc = new JdbcTemplate(
                            new SingleConnectionDataSource(connection, true));
                    UUID adminId = UUID.randomUUID();
                    UUID feedbackId = UUID.randomUUID();
                    jdbc.update(
                            "INSERT INTO admins (id, username, password) VALUES (?, ?, 'password')",
                            adminId,
                            "feedback-audit-admin"
                    );

                    // When: 문의 삭제 감사는 사유 없이 저장할 수 있다.
                    jdbc.update(
                            """
                                    INSERT INTO admin_audit_logs
                                        (actor_admin_id, action, target_type, target_id, before_state, after_state, occurred_at, request_id)
                                    VALUES (?, 'FEEDBACK_DELETED', 'FEEDBACK', ?, '{"deletedAt":null}',
                                        '{"deletedAt":"2026-11-03T12:00:00Z"}', CURRENT_TIMESTAMP, ?)
                                    """,
                            adminId,
                            feedbackId,
                            UUID.randomUUID()
                    );

                    // Then: 같은 감사에서 대상 유형만 바꾸면 DB 제약이 거절한다.
                    assertThatThrownBy(() -> jdbc.update(
                            """
                                    INSERT INTO admin_audit_logs
                                        (actor_admin_id, action, target_type, target_id, before_state, after_state, occurred_at, request_id)
                                    SELECT actor_admin_id, action, 'POST', target_id, before_state, after_state, occurred_at, request_id
                                    FROM admin_audit_logs WHERE target_id = ?
                                    """,
                            feedbackId
                    )).isInstanceOf(DataIntegrityViolationException.class)
                            .hasMessageContaining("ck_admin_audit_logs_action_target");
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs",
                            Long.class))
                            .isEqualTo(1L);
                } finally {
                    connection.setSchema(previousSchema);
                }
            }
        } finally {
            admin.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    @DisplayName("전환 트랜잭션 시작 전 문의만 숨기고 정확한 경계와 이후 문의는 보존한다")
    void migrate_cutoverBoundary_keepsSubmissionsAtOrAfterTransactionStart() throws Exception {
        // Given
        String schema = "feedback_cutover_" + UUID.randomUUID().toString().replace("-", "");
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
                            userId + "@cutover.test",
                            "signatures/" + userId
                    );
                    UUID beforeId = UUID.randomUUID();
                    UUID boundaryId = UUID.randomUUID();
                    UUID afterId = UUID.randomUUID();
                    AtomicReference<Instant> cutover = new AtomicReference<>();

                    // When: 같은 마이그레이션 트랜잭션에서 경계 직전·동일·직후 접수 시각을 준비한다.
                    Flyway.configure()
                            .dataSource(dataSource)
                            .defaultSchema(schema)
                            .schemas(schema)
                            .locations("classpath:db/migration")
                            .target("202611031610")
                            .callbacks(new BaseCallback() {
                                @Override
                                public boolean supports(Event event, Context context) {
                                    return event == Event.BEFORE_EACH_MIGRATE;
                                }

                                @Override
                                public void handle(Event event, Context context) {
                                    JdbcTemplate migrationJdbc = new JdbcTemplate(
                                            new SingleConnectionDataSource(context.getConnection(),
                                                    true));
                                    Instant start = migrationJdbc.queryForObject(
                                            "SELECT transaction_timestamp()", Timestamp.class)
                                            .toInstant();
                                    cutover.set(start);
                                    insertFeedbackAt(
                                            migrationJdbc,
                                            beforeId,
                                            userId,
                                            start.minusNanos(1000)
                                    );
                                    insertFeedbackAt(
                                            migrationJdbc,
                                            boundaryId,
                                            userId,
                                            start
                                    );
                                    insertFeedbackAt(
                                            migrationJdbc,
                                            afterId,
                                            userId,
                                            start.plusNanos(1000)
                                    );
                                }
                            })
                            .load().migrate();

                    // Then: 백필 시각도 같은 트랜잭션 기준임을 확인해 테스트의 경계를 고정한다.
                    assertThat(jdbc.queryForObject(
                            "SELECT deleted_at FROM feedbacks WHERE id = ?",
                            Timestamp.class,
                            beforeId
                    ).toInstant()).isEqualTo(cutover.get());
                    assertThat(jdbc.queryForList(
                            "SELECT id FROM feedbacks WHERE deleted_at IS NULL",
                            UUID.class
                    )).containsExactlyInAnyOrder(boundaryId, afterId);
                } finally {
                    connection.setSchema(previousSchema);
                }
            }
        } finally {
            admin.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

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

    private void insertFeedbackAt(
            JdbcTemplate jdbc,
            UUID feedbackId,
            UUID userId,
            Instant createdAt
    ) {
        jdbc.update(
                "INSERT INTO feedbacks (id, user_id, content, created_at) VALUES (?, ?, '전환 경계 문의', ?)",
                feedbackId,
                userId,
                Timestamp.from(createdAt)
        );
    }
}
