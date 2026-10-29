package com.chalkak.backend.appversion.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Connection;
import java.util.Arrays;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AppVersionMigrationValidationTest extends IntegrationTestSupport {
    @Autowired
    private Flyway flyway;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("""
                CREATE TEMPORARY TABLE app_versions (
                    id UUID PRIMARY KEY, platform TEXT,
                    minimum_supported_version TEXT, store_url TEXT
                )
                """);
    }

    @ParameterizedTest
    @CsvSource({
            "IOS, 01.2.0, https://apps.apple.com/app/id123",
            "IOS, 2147483648.0.0, https://apps.apple.com/app/id123",
            "IOS, 1.0.0, https://itunes.apple.com/app/id123",
            "IOS, 1.0.0, https://apps.apple.com/app/id123?uo=4",
            "ANDROID, 1.0.0, https://apps.apple.com/app/id123",
            "ANDROID, 1.0.0, https://play.google.com/store/apps/details?id=com.a.b&referrer=sample",
            "IOS, 1.0.0, https://apps.apple.com/app/token=example-secret/id123"
    })
    @DisplayName("기존 버전·주소·플랫폼·감사 값이 유효하지 않으면 변경 전에 사전 검증으로 중단한다")
    void beforeMigrate_invalidLegacySetting_rejectsWithoutLeakingValues(
            String platform,
            String version,
            String url
    ) {
        // Given
        UUID id = insertSetting(
                platform,
                version,
                url
        );
        // When & Then
        assertThatThrownBy(this::validateBeforeMigrate)
                .hasMessageContaining(id.toString())
                .hasMessageNotContaining(url);
        assertThat(jdbc.queryForObject("SELECT store_url FROM app_versions", String.class))
                .isEqualTo(url);
    }

    @Test
    @DisplayName("기존 주소의 개행을 조용히 지우지 않고 저장 원문 검증에서 중단한다")
    void beforeMigrate_whitespaceLegacySetting_preservesAndRejectsRawValue() {
        // Given
        String raw = "https://apps.apple.com/app/id123\n";
        insertSetting(
                "IOS",
                "1.0.0",
                raw
        );
        // When & Then
        assertThatThrownBy(this::validateBeforeMigrate).hasMessageContaining("사전 검증");
        assertThat(jdbc.queryForObject("SELECT store_url FROM app_versions", String.class))
                .isEqualTo(raw);
    }

    @Test
    @DisplayName("유효한 기존 설정과 빈 테이블은 값을 바꾸지 않고 마이그레이션을 허용한다")
    void beforeMigrate_validOrEmptyTable_preservesValues() {
        // When & Then
        assertThatCode(this::validateBeforeMigrate).doesNotThrowAnyException();
        insertSetting(
                "IOS",
                "1.0.0",
                "https://apps.apple.com/app/id123"
        );
        assertThatCode(this::validateBeforeMigrate).doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("SELECT minimum_supported_version FROM app_versions",
                String.class)).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("첫 설치로 앱 버전 테이블이 아직 없으면 사전 검증을 건너뛴다")
    void beforeMigrate_tableNotCreated_allowsInitialInstallation() {
        // Given
        jdbc.execute("DROP TABLE pg_temp.app_versions");
        jdbc.execute("SET LOCAL search_path TO pg_catalog");
        // When & Then
        assertThatCode(this::validateBeforeMigrate).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "\n", "\t"})
    @DisplayName("새 DB 제약은 주소 원문의 후행 공백·개행·탭을 거절한다")
    void insert_whitespaceUrl_rejectsAtDatabaseBoundary(String whitespace) {
        // When & Then
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO public.app_versions (platform, minimum_supported_version, store_url) VALUES ('IOS', '1.0.0', ?)",
                "https://apps.apple.com/app/id123" + whitespace
        )).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_app_versions_store_url");
    }

    @Test
    @DisplayName("앱 버전 감사 사유 누락은 공통 필수 사유 제약으로 거절한다")
    void insert_missingAuditReason_usesSharedReasonConstraint() {
        // Given
        UUID admin = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO admins (id, username, password, created_at, updated_at) VALUES (?, ?, 'password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                admin,
                "app-version-reason-" + admin
        );
        // When & Then
        assertThatThrownBy(() -> jdbc.update(
                """
                        INSERT INTO admin_audit_logs (actor_admin_id, action, target_type, target_id,
                            reason, before_state, after_state, occurred_at, request_id)
                        VALUES (?, 'APP_VERSION_CREATED', 'APP_VERSION', ?, NULL, '{}',
                            '{"platform":"IOS"}', CURRENT_TIMESTAMP, ?)
                        """,
                admin,
                UUID.randomUUID(),
                UUID.randomUUID()
        )).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_admin_audit_logs_required_reason");
    }

    @Test
    @DisplayName("사전 검증 DB 조회가 실패하면 유효한 데이터로 간주하지 않고 시작을 중단한다")
    void beforeMigrate_databaseReadFailure_rejectsMigration() {
        // Given
        jdbc.execute("ALTER TABLE pg_temp.app_versions DROP COLUMN store_url");
        // When & Then
        assertThatThrownBy(this::validateBeforeMigrate)
                .isInstanceOf(FlywayException.class).hasMessageContaining("DB 조회");
    }

    private void validateBeforeMigrate() {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            Context context = mock(Context.class);
            given(context.getConnection()).willReturn(connection);
            var callbacks = Arrays.stream(flyway.getConfiguration().getCallbacks())
                    .filter(callback -> callback.supports(Event.BEFORE_MIGRATE, context)).toList();
            assertThat(callbacks).isNotEmpty();
            callbacks.forEach(callback -> callback.handle(Event.BEFORE_MIGRATE, context));
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private UUID insertSetting(
            String platform,
            String version,
            String url
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO app_versions (id, platform, minimum_supported_version, store_url) VALUES (?, ?, ?, ?)",
                id,
                platform,
                version,
                url
        );
        return id;
    }
}
