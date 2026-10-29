package com.chalkak.backend.appversion.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
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

class AppVersionRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbcTemplate).clean();
    }

    @AfterEach
    void tearDown() {
        new DatabaseCleaner(jdbcTemplate).clean();
    }

    @Test
    @DisplayName("중복 플랫폼 저장이 경쟁해도 하나만 저장할 수 있는 고유 제약을 유지한다")
    void insert_concurrentDuplicatePlatform_savesOnlyOne() throws Exception {
        // Given
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        // When
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> insertAfterStart(ready, start));
            var second = executor.submit(() -> insertAfterStart(ready, start));
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }

            // Then
            assertThat(java.util.List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_versions", Integer.class))
                .isEqualTo(1);
    }

    private boolean insertAfterStart(CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            jdbcTemplate.update("""
                    INSERT INTO app_versions (platform, minimum_supported_version, store_url)
                    VALUES ('ANDROID', '1.2.0',
                        'https://play.google.com/store/apps/details?id=com.example.chalkak')
                    """);
            return true;
        } catch (DataIntegrityViolationException exception) {
            assertThat(exception.getMostSpecificCause().getMessage())
                    .contains("uq_app_versions_platform");
            return false;
        }
    }
}
