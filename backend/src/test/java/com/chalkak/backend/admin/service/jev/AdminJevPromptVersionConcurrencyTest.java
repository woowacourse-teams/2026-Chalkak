package com.chalkak.backend.admin.service.jev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.chalkak.backend.admin.service.audit.AdminAuditLogService;
import com.chalkak.backend.admin.service.jev.AdminJevPromptVersionService.CreateCommand;
import com.chalkak.backend.jev.domain.JevPromptFixtures;
import com.chalkak.backend.jev.domain.JevPromptRole;
import com.chalkak.backend.jev.domain.JevPromptVersion;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

class AdminJevPromptVersionConcurrencyTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID = UUID.fromString("019ad963-213b-734e-a261-60e568c12056");

    @Autowired
    private AdminJevPromptVersionService service;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private AdminAuditLogService audit;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbc).clean();
        cleanOwnRows();
        jdbc.update(
                """
                        INSERT INTO admins (id, username, password, created_at, updated_at)
                        VALUES (?, 'jev-concurrency-test', 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                ADMIN_ID);
    }

    @AfterEach
    void tearDown() {
        cleanOwnRows();
    }

    @Test
    @DisplayName("동시 저장도 용도 잠금으로 서로 다른 연속 버전을 부여한다")
    void createVersion_concurrent_assignsDistinctSequentialVersions() throws Exception {
        // When
        List<JevPromptVersion> result = runTogether(this::create, this::create);

        // Then
        assertThat(result).extracting(JevPromptVersion::getVersion).containsExactlyInAnyOrder(1, 2);
        assertThat(activeCount()).isZero();
    }

    @Test
    @DisplayName("서로 다른 버전을 동시에 적용해도 한 버전만 활성화된다")
    void updateActivation_concurrent_keepsOneActiveVersion() throws Exception {
        // Given
        UUID first = create().getId();
        UUID second = create().getId();

        // When
        runTogether(() -> service.updateActivation(
                ADMIN_ID,
                first,
                true
        ), () -> service.updateActivation(
                ADMIN_ID,
                second,
                true
        ));

        // Then
        assertThat(activeCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT id FROM ai_prompt_versions WHERE active AND type = 'JEV_PHOTO_MODERATION'",
                UUID.class
        )).isIn(first, second);
    }

    @Test
    @DisplayName("적용 감사가 실패하면 이전 활성 버전과 새 버전의 상태가 함께 롤백된다")
    void updateActivation_auditFails_rollsBackBothVersions() {
        // Given
        UUID first = create().getId();
        UUID second = create().getId();
        service.updateActivation(
                ADMIN_ID,
                first,
                true
        );
        AdminAuditLogService auditTarget = AopTestUtils.getUltimateTargetObject(audit);
        doThrow(new IllegalStateException("감사 저장 실패")).when(auditTarget).createAuditLog(any());

        // When & Then
        assertThatThrownBy(() -> service.updateActivation(
                ADMIN_ID,
                second,
                true
        )).isInstanceOf(IllegalStateException.class).hasMessage("감사 저장 실패");
        assertThat(activeCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT id FROM ai_prompt_versions WHERE active AND type = 'JEV_PHOTO_MODERATION'",
                UUID.class
        )).isEqualTo(first);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs WHERE actor_admin_id = ?",
                Long.class,
                ADMIN_ID
        )).isEqualTo(3);
    }

    @Test
    @DisplayName("프롬프트 생성 감사가 실패하면 새 버전도 저장되지 않는다")
    void createVersion_auditFails_rollsBackNewVersion() {
        // Given
        AdminAuditLogService auditTarget = AopTestUtils.getUltimateTargetObject(audit);
        doThrow(new IllegalStateException("감사 저장 실패")).when(auditTarget).createAuditLog(any());

        // When & Then
        assertThatThrownBy(this::create).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_prompt_versions WHERE created_by_admin_id = ?",
                Long.class,
                ADMIN_ID
        )).isZero();
    }

    private JevPromptVersion create() {
        return service.createVersion(ADMIN_ID, new CreateCommand(
                JevPromptRole.JEV_PHOTO_MODERATION,
                "jev-1.14.0",
                JevPromptFixtures.photo(),
                null
        ));
    }

    private long activeCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ai_prompt_versions WHERE active",
                Long.class);
    }

    private <T> List<T> runTogether(Callable<T> first, Callable<T> second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<T> firstResult = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return first.call();
            });
            Future<T> secondResult = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return second.call();
            });
            return List.of(firstResult.get(15, TimeUnit.SECONDS),
                    secondResult.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void cleanOwnRows() {
        jdbc.update("DELETE FROM admin_audit_logs WHERE actor_admin_id = ?", ADMIN_ID);
        jdbc.update("DELETE FROM ai_prompt_versions WHERE created_by_admin_id = ?", ADMIN_ID);
        jdbc.update("DELETE FROM admins WHERE id = ?", ADMIN_ID);
    }
}
