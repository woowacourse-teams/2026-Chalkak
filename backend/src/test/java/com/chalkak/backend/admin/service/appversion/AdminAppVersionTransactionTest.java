package com.chalkak.backend.admin.service.appversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.appversion.domain.AppPlatform;
import com.chalkak.backend.appversion.domain.ChangeReason;
import com.chalkak.backend.appversion.domain.MinimumSupportedVersion;
import com.chalkak.backend.appversion.domain.StoreUrl;
import com.chalkak.backend.appversion.repository.AppVersionRepository;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AdminAppVersionTransactionTest extends IntegrationTestSupport {
    private static final UUID ADMIN = UUID.fromString("019a0000-0000-7000-8000-000000000848");
    private static final String URL = "https://play.google.com/store/apps/details?id=com.example.chalkak";
    @Autowired
    private AdminAppVersionService service;
    @Autowired
    private AppVersionRepository repository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        clean();
        jdbc.update(
                "INSERT INTO admins (id, username, password, created_at, updated_at) VALUES (?, 'app-version-transaction', 'password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                ADMIN);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    @DisplayName("경쟁하는 최초 저장과 같은 revision 수정은 한 요청만 변경하고 다른 요청은 충돌로 거절한다")
    void saveAppVersion_concurrentRequests_preventsLostUpdates() throws Exception {
        // When & Then
        assertThat(race(null)).containsExactlyInAnyOrder("saved", "conflict");
        assertThat(count("app_versions")).isEqualTo(1);
        assertThat(countAudits()).isEqualTo(1);
        assertThat(race(0L)).containsExactlyInAnyOrder("saved", "conflict");
        assertThat(countAudits()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT revision FROM app_versions", Long.class))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("최초 저장 재시도가 동시에 와도 같은 값은 한 행과 한 감사 이력만 남긴다")
    void saveAppVersion_identicalConcurrentCreate_isIdempotent() throws Exception {
        // Given
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                await(start);
                return save(null, "1.0.0");
            });
            var second = executor.submit(() -> {
                await(start);
                return save(null, "1.0.0");
            });
            // When
            start.countDown();
            var a = first.get(10, TimeUnit.SECONDS);
            var b = second.get(10, TimeUnit.SECONDS);
            // Then
            assertThat(a).isEqualTo(b);
            assertThat(count("app_versions")).isEqualTo(1);
            assertThat(countAudits()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("플랫폼 잠금을 유지하면 같은 플랫폼만 대기하고 다른 플랫폼 저장은 완료된다")
    void lockPlatform_heldTransaction_serializesOnlySamePlatform() throws Exception {
        // Given
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var holder = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        repository.lockPlatform(AppPlatform.ANDROID);
                        locked.countDown();
                        await(release);
                    }));
            try {
                await(locked);
                var blocked = executor.submit(() -> save(null, "1.0.0"));
                // When & Then
                assertThatThrownBy(() -> blocked.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
                executor.submit(() -> service.saveAppVersion(
                        ADMIN,
                        AppPlatform.IOS,
                        null,
                        "1.0.0",
                        "https://apps.apple.com/app/id123",
                        "출시 확인"
                )).get(5, TimeUnit.SECONDS);
                release.countDown();
                holder.get(10, TimeUnit.SECONDS);
                blocked.get(10, TimeUnit.SECONDS);
                assertThat(count("app_versions")).isEqualTo(2);
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    @DisplayName("플랫폼 잠금을 우회한 직접 수정도 낙관적 잠금으로 한 요청만 반영하고 충돌을 업무 예외로 바꾼다")
    void saveAndFlush_concurrentDirectUpdates_convertsOptimisticConflict() throws Exception {
        // Given
        save(null, "1.0.0");
        CountDownLatch loaded = new CountDownLatch(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            // When
            var first = executor.submit(() -> updateDirectly(loaded, "2.0.0"));
            var second = executor.submit(() -> updateDirectly(loaded, "3.0.0"));
            // Then
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("saved", "conflict");
            assertThat(jdbc.queryForObject("SELECT revision FROM app_versions", Long.class))
                    .isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("감사 저장 실패는 최초 생성과 변경된 값·시각·revision을 모두 롤백한다")
    void saveAppVersion_auditFailure_rollsBackEntireChange() {
        // Given
        jdbc.execute(
                "ALTER TABLE admin_audit_logs ADD CONSTRAINT ck_test_app_version_audit CHECK (target_type <> 'APP_VERSION')");
        try {
            // When & Then
            assertThatThrownBy(() -> save(null, "1.0.0"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(count("app_versions")).isZero();
        } finally {
            jdbc.execute("ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_test_app_version_audit");
        }
        var first = save(null, "1.0.0");
        jdbc.execute(
                "ALTER TABLE admin_audit_logs ADD CONSTRAINT ck_test_app_version_audit CHECK (target_type <> 'APP_VERSION') NOT VALID");
        try {
            assertThatThrownBy(() -> save(0L, "2.0.0"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(service.getAppVersions().get(0)).isEqualTo(first);
            assertThat(countAudits()).isEqualTo(1);
        } finally {
            jdbc.execute("ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_test_app_version_audit");
        }
    }

    @Test
    @DisplayName("감사에 기록할 수 없는 스토어 주소는 최초 입력과 기존 설정 변경을 모두 롤백한다")
    void saveAppVersion_sensitiveStoreUrl_rollsBackCreationAndUpdate() {
        // Given
        String sensitiveUrl = "https://apps.apple.com/app/token=example-secret/id123";
        // When & Then
        assertThatThrownBy(() -> service.saveAppVersion(
                ADMIN,
                AppPlatform.IOS,
                null,
                "1.0.0",
                sensitiveUrl,
                "출시 확인"
        )).isInstanceOf(BusinessException.class);
        assertThat(count("app_versions")).isZero();
        assertThat(countAudits()).isZero();
        var first = service.saveAppVersion(
                ADMIN,
                AppPlatform.IOS,
                null,
                "1.0.0",
                "https://apps.apple.com/app/id123",
                "출시 확인"
        );
        assertThatThrownBy(() -> service.saveAppVersion(
                ADMIN,
                AppPlatform.IOS,
                first.revision(),
                "2.0.0",
                sensitiveUrl,
                "주소 변경"
        )).isInstanceOf(BusinessException.class);
        assertThat(service.getAppVersions().get(1)).isEqualTo(first);
        assertThat(countAudits()).isEqualTo(1);
    }

    private List<String> race(Long revision) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                await(start);
                return outcome(revision, revision == null ? "1.0.0" : "3.0.0");
            });
            var second = executor.submit(() -> {
                await(start);
                return outcome(revision, revision == null ? "2.0.0" : "4.0.0");
            });
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
    }

    private String outcome(Long revision, String version) {
        try {
            save(revision, version);
            return "saved";
        } catch (BusinessException error) {
            assertThat(error.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_STATE_CHANGED);
            return "conflict";
        }
    }

    private String updateDirectly(CountDownLatch loaded, String version) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                var setting = repository.findByPlatform(AppPlatform.ANDROID).orElseThrow();
                loaded.countDown();
                await(loaded);
                setting.update(
                        new MinimumSupportedVersion(version),
                        new StoreUrl(URL),
                        ADMIN,
                        new ChangeReason("직접 수정 경쟁")
                );
                repository.saveAndFlush(setting);
            });
            return "saved";
        } catch (BusinessException error) {
            assertThat(error.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_STATE_CHANGED);
            return "conflict";
        }
    }

    private AdminAppVersionService.SettingResult save(Long revision, String version) {
        return service.saveAppVersion(
                ADMIN,
                AppPlatform.ANDROID,
                revision,
                version,
                URL,
                "출시 확인"
        );
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
    private int countAudits() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM admin_audit_logs WHERE target_type='APP_VERSION'",
                Integer.class);
    }
    private void clean() {
        new DatabaseCleaner(jdbc).clean();
        jdbc.update("DELETE FROM admin_audit_logs WHERE actor_admin_id=?", ADMIN);
        jdbc.update("DELETE FROM admins WHERE id=?", ADMIN);
    }
}
