package com.chalkak.backend.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.notification.domain.FcmToken;
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

class PushDeviceMigrationTest extends IntegrationTestSupport {

    private static final Instant REGISTERED_AT = Instant.parse("2026-09-30T00:00:00Z");
    private static final Instant DISABLED_AT = REGISTERED_AT.plusSeconds(60);

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("로그인 테이블 없이 기기 테이블을 생성하고 기존 RT 회전 기록과 수신 설정을 보존한다")
    void migrate_existingRotatedTokens_preservesDataAndCreatesDevicesWithoutLoginTable()
            throws Exception {
        // given: 기기 테이블 생성 전 버전에 기존 RT와 수신 설정을 준비한다.
        String schema = "push_device_migration_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate admin = new JdbcTemplate(dataSource);
        admin.execute("CREATE SCHEMA " + schema);
        try {
            migrate(schema, "202609301100");
            try (Connection connection = dataSource.getConnection()) {
                String previousSchema = connection.getSchema();
                connection.setSchema(schema);
                try {
                    JdbcTemplate jdbc = new JdbcTemplate(
                            new SingleConnectionDataSource(connection, true));
                    UUID userId = insertUser(jdbc, "active");
                    UUID sessionId = UUID.randomUUID();
                    insertToken(jdbc, userId, sessionId, "1".repeat(64), REGISTERED_AT);
                    insertToken(jdbc, userId, sessionId, "2".repeat(64), null);
                    List<Map<String, Object>> tokensBefore = jdbc.queryForList(
                            "SELECT * FROM user_refresh_tokens ORDER BY id");
                    List<Map<String, Object>> settingsBefore = jdbc.queryForList("""
                            SELECT id, topic_push_enabled, moderation_push_enabled
                            FROM users ORDER BY id
                            """);

                    // when
                    migrate(schema, "202609301400");

                    // then
                    assertThat(jdbc.queryForList("SELECT * FROM push_devices ORDER BY id"))
                            .isEmpty();
                    assertThat(jdbc.queryForList("SELECT * FROM user_refresh_tokens ORDER BY id"))
                            .isEqualTo(tokensBefore);
                    assertThat(jdbc.queryForList("""
                            SELECT id, topic_push_enabled, moderation_push_enabled
                            FROM users ORDER BY id
                            """))
                            .isEqualTo(settingsBefore);
                    assertThat(jdbc.queryForObject("""
                            SELECT COUNT(*) FROM information_schema.tables
                            WHERE table_schema = ? AND table_name = 'login_sessions'
                            """, Integer.class, schema)).isZero();
                    assertThat(jdbc.queryForObject("""
                            SELECT COUNT(*) FROM flyway_schema_history
                            WHERE version IN ('202609301130', '202610061400', '202610061500')
                            """, Integer.class)).isZero();
                    // 생성된 최종 구조는 활성 기기와 연결이 제거된 비활성 행을 저장할 수 있다.
                    insertDevice(jdbc, userId, sessionId, "active-token", null);
                    insertDevice(jdbc, null, null, null, DISABLED_AT);
                    assertThat(jdbc.queryForObject("""
                            SELECT COUNT(*) FROM push_devices
                            WHERE user_id = ? AND session_id = ? AND fcm_token = 'active-token'
                                AND disabled_at IS NULL
                            """, Integer.class, userId, sessionId)).isEqualTo(1);
                    assertThat(jdbc.queryForObject("""
                            SELECT COUNT(*) FROM push_devices
                            WHERE disabled_at = ? AND user_id IS NULL AND session_id IS NULL
                                AND fcm_token IS NULL AND fcm_token_hash IS NULL
                            """, Integer.class, Timestamp.from(DISABLED_AT))).isEqualTo(1);
                    assertThat(jdbc.queryForObject("""
                            SELECT COUNT(*) FROM information_schema.columns
                            WHERE table_schema = ? AND table_name = 'push_devices'
                                AND column_name IN ('installation_id', 'platform')
                            """, Integer.class, schema)).isZero();
                } finally {
                    connection.setSchema(previousSchema);
                }
            }
        } finally {
            admin.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private void migrate(String schema, String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .defaultSchema(schema)
                .schemas(schema)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private UUID insertUser(JdbcTemplate jdbc, String name) {
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, email, signature_original_storage_key,
                    topic_push_enabled, moderation_push_enabled)
                VALUES (?, ?, ?, false, true)
                """, userId, name + "@test.chalkak", "signatures/" + name);
        return userId;
    }

    private void insertDevice(
            JdbcTemplate jdbc,
            UUID userId,
            UUID sessionId,
            String token,
            Instant disabledAt
    ) {
        jdbc.update("""
                INSERT INTO push_devices (user_id, session_id, fcm_token, fcm_token_hash,
                    registered_at, updated_at, disabled_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, userId, sessionId, token, token == null ? null : new FcmToken(token).getHash(),
                Timestamp.from(REGISTERED_AT), Timestamp.from(DISABLED_AT),
                disabledAt == null ? null : Timestamp.from(disabledAt));
    }

    private void insertToken(
            JdbcTemplate jdbc,
            UUID userId,
            UUID sessionId,
            String tokenHash,
            Instant rotatedAt
    ) {
        jdbc.update("""
                INSERT INTO user_refresh_tokens (user_id, session_id, token_hash,
                    expires_at, absolute_expires_at, rotated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, userId, sessionId, tokenHash,
                Timestamp.from(REGISTERED_AT.plusSeconds(3600)),
                Timestamp.from(REGISTERED_AT.plusSeconds(7200)),
                rotatedAt == null ? null : Timestamp.from(rotatedAt));
    }
}
