package com.chalkak.backend.appversion.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.appversion.domain.AppPlatform;
import com.chalkak.backend.appversion.domain.AppVersion;
import com.chalkak.backend.appversion.domain.MinimumSupportedVersion;
import com.chalkak.backend.appversion.domain.StoreUrl;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AppVersionTimestampTest extends IntegrationTestSupport {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("기존 설정의 데이터는 보존하고 새 시각 컬럼을 마이그레이션 적용 시각으로 채운다")
    void migrate_existingSettings_preservesValuesAndBackfillsTimestamps() throws IOException {
        // Given
        // 임시 테이블은 같은 트랜잭션 안에서만 실제 테이블을 가려 기존 스키마의 업그레이드를 검증한다.
        String originalSchema = migration("V202610282310__create_app_versions.sql")
                .replace("CREATE TABLE app_versions", "CREATE TEMPORARY TABLE app_versions");
        jdbcTemplate.execute(originalSchema);
        UUID id = insertWithSqlDefaults();
        Instant transactionTime = currentTransactionTime();

        // When
        jdbcTemplate.execute(migration("V202610291030__add_app_version_timestamps.sql"));

        // Then
        assertThat(jdbcTemplate.queryForObject(
                "SELECT minimum_supported_version FROM app_versions WHERE id = ?",
                String.class,
                id
        )).isEqualTo("1.2.0");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT store_url FROM app_versions WHERE id = ?",
                String.class,
                id
        )).isEqualTo("https://play.google.com/store/apps/details?id=com.example.chalkak");
        assertThat(readTimestamp(id, "created_at")).isEqualTo(transactionTime);
        assertThat(readTimestamp(id, "updated_at")).isEqualTo(transactionTime);
    }

    @Test
    @DisplayName("기존 설정에 관리자 기능을 적용해도 값·시각을 유지하고 revision 0과 미상 메타데이터로 조회한다")
    void migrate_existingSettings_addsManagementWithoutInventingHistory() throws IOException {
        // Given
        jdbcTemplate.execute(migration("V202610282310__create_app_versions.sql")
                .replace("CREATE TABLE app_versions", "CREATE TEMPORARY TABLE app_versions"));
        UUID id = insertWithSqlDefaults();
        jdbcTemplate.execute(migration("V202610291030__add_app_version_timestamps.sql"));
        Instant createdAt = readTimestamp(id, "created_at");
        // When
        // PostgreSQL 임시 테이블의 FK 대상도 임시 테이블이어야 한다.
        jdbcTemplate.execute("CREATE TEMPORARY TABLE admins (id UUID PRIMARY KEY)");
        jdbcTemplate.execute("""
                CREATE TEMPORARY TABLE admin_audit_logs (
                    action admin_action, target_type admin_target_type, reason TEXT,
                    CONSTRAINT ck_admin_audit_logs_action_target CHECK (TRUE),
                    CONSTRAINT ck_admin_audit_logs_required_reason CHECK (TRUE)
                )
                """);
        jdbcTemplate.execute(migration("V202610291401__add_app_version_management.sql"));
        entityManager.flush();
        entityManager.clear();
        AppVersion migrated = entityManager.find(AppVersion.class, id);
        // Then
        assertThat(migrated.getMinimumSupportedVersion().value()).isEqualTo("1.2.0");
        assertThat(migrated.getStoreUrl().value())
                .isEqualTo("https://play.google.com/store/apps/details?id=com.example.chalkak");
        assertThat(migrated.getRevision()).isZero();
        assertThat(migrated.getLastChangedBy()).isNull();
        assertThat(migrated.getChangeReason()).isNull();
        assertThat(migrated.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("시각을 지정하지 않은 SQL 입력은 두 시각의 DB 기본값을 저장한다")
    void insert_withoutExplicitTimestamps_usesDatabaseDefaults() {
        // Given
        Instant transactionTime = currentTransactionTime();

        // When
        UUID id = insertWithSqlDefaults();

        // Then
        assertThat(readTimestamp(id, "created_at")).isEqualTo(transactionTime);
        assertThat(readTimestamp(id, "updated_at")).isEqualTo(transactionTime);
    }

    @Test
    @DisplayName("JPA로 설정을 생성하면 생성·수정 시각을 기록한다")
    void persist_newSetting_recordsBothTimestamps() {
        // Given
        AppVersion appVersion = createEntityFixture();
        Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

        // When
        entityManager.persist(appVersion);
        entityManager.flush();
        UUID id = appVersion.getId();
        entityManager.clear();

        // Then
        AppVersion stored = entityManager.find(AppVersion.class, id);
        Instant after = Instant.now();
        assertThat(stored.getCreatedAt()).isBetween(before, after);
        assertThat(stored.getUpdatedAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("JPA로 설정을 바꾸면 생성 시각을 유지하고 수정 시각만 갱신한다")
    void update_existingSetting_keepsCreatedAtAndRefreshesUpdatedAt() {
        // Given
        UUID id = insertWithSqlDefaults();
        Instant originalTime = Instant.parse("2026-10-01T00:00:00Z");
        jdbcTemplate.update(
                "UPDATE app_versions SET created_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(originalTime),
                Timestamp.from(originalTime),
                id
        );
        entityManager.flush();
        entityManager.clear();
        AppVersion appVersion = entityManager.find(AppVersion.class, id);

        // When
        ReflectionTestUtils.setField(
                appVersion,
                "minimumSupportedVersion",
                new MinimumSupportedVersion("1.3.0")
        );
        entityManager.flush();
        entityManager.clear();

        // Then
        AppVersion stored = entityManager.find(AppVersion.class, id);
        assertThat(stored.getMinimumSupportedVersion().value()).isEqualTo("1.3.0");
        assertThat(stored.getCreatedAt()).isEqualTo(originalTime);
        assertThat(stored.getUpdatedAt()).isAfter(originalTime);
    }

    // 관리자 쓰기 API를 추가하지 않고 JPA 시각 매핑만 검증하는 영속성 fixture다.
    private AppVersion createEntityFixture() {
        AppVersion appVersion = BeanUtils.instantiateClass(AppVersion.class);
        ReflectionTestUtils.setField(
                appVersion,
                "platform",
                AppPlatform.ANDROID
        );
        ReflectionTestUtils.setField(
                appVersion,
                "minimumSupportedVersion",
                new MinimumSupportedVersion("1.2.0")
        );
        ReflectionTestUtils.setField(
                appVersion,
                "storeUrl",
                new StoreUrl("https://play.google.com/store/apps/details?id=com.example.chalkak")
        );
        return appVersion;
    }

    private UUID insertWithSqlDefaults() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO app_versions (platform, minimum_supported_version, store_url)
                VALUES ('ANDROID', '1.2.0',
                    'https://play.google.com/store/apps/details?id=com.example.chalkak')
                RETURNING id
                """, UUID.class);
    }

    private Instant currentTransactionTime() {
        return jdbcTemplate.queryForObject("SELECT CURRENT_TIMESTAMP", OffsetDateTime.class)
                .toInstant();
    }

    private Instant readTimestamp(UUID id, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM app_versions WHERE id = ?",
                OffsetDateTime.class,
                id
        ).toInstant();
    }

    private String migration(String fileName) throws IOException {
        return new ClassPathResource("db/migration/" + fileName)
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
