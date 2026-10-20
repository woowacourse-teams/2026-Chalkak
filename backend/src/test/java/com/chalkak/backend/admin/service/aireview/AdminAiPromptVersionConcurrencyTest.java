package com.chalkak.backend.admin.service.aireview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.admin.service.aireview.AdminAiPromptVersionService.CreateCommand;
import com.chalkak.backend.admin.service.aireview.AdminAiPromptVersionService.VersionResult;
import com.chalkak.backend.aireview.domain.AiPromptType;
import com.chalkak.backend.common.logging.LogFields;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminAiPromptVersionConcurrencyTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000689");
    private static final String MODEL = "gpt-6.1-sol";
    private static final int THREADS = 5;

    @Autowired
    private AdminAiPromptVersionService versionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        clean();
        jdbcTemplate.update("""
                INSERT INTO admins (id, username, password, created_at, updated_at)
                VALUES (?, 'prompt-version-concurrency', 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ADMIN_ID);
        given(aiReviewGenerator.supportsModel(MODEL)).willReturn(true);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    @DisplayName("같은 유형에 동시에 버전을 만들어도 중복·유니크 위반 없이 1부터 5까지 부여된다")
    void createVersion_concurrent_assignsDistinctVersions() throws Exception {
        // Given
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<VersionResult>> futures = new ArrayList<>();

        // When
        try (ExecutorService executor = Executors.newFixedThreadPool(THREADS)) {
            for (int index = 0; index < THREADS; index++) {
                String systemPrompt = "동시 시스템 " + index;
                futures.add(executor.submit(createAfterStart(ready, start, systemPrompt)));
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            List<Integer> versions = new ArrayList<>();
            for (Future<VersionResult> future : futures) {
                versions.add(future.get(20, TimeUnit.SECONDS).version());
            }

            // Then
            assertThat(versions).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
        }
        assertThat(jdbcTemplate.queryForList(
                "SELECT version FROM ai_prompt_versions ORDER BY version", Integer.class))
                .containsExactly(1, 2, 3, 4, 5);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admin_audit_logs WHERE action = 'AI_PROMPT_VERSION_CREATED'
                """, Integer.class)).isEqualTo(THREADS);
    }

    private Callable<VersionResult> createAfterStart(
            CountDownLatch ready, CountDownLatch start, String systemPrompt) {
        return () -> {
            MDC.put(LogFields.REQUEST_ID, UUID.randomUUID().toString());
            try {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return versionService.createVersion(ADMIN_ID, new CreateCommand(
                        AiPromptType.PHOTO_REVIEW, systemPrompt, "{{topicTitle}}", MODEL,
                        null, null, null, null, null));
            } finally {
                MDC.remove(LogFields.REQUEST_ID);
            }
        };
    }

    private void clean() {
        jdbcTemplate.update("DELETE FROM admin_audit_logs WHERE actor_admin_id = ?", ADMIN_ID);
        jdbcTemplate.update("DELETE FROM ai_prompt_versions WHERE created_by_admin_id = ?", ADMIN_ID);
        jdbcTemplate.update("DELETE FROM admins WHERE id = ?", ADMIN_ID);
    }
}
