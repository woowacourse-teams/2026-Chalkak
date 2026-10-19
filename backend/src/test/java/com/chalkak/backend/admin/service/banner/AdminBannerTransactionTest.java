package com.chalkak.backend.admin.service.banner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminBannerTransactionTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000676");
    private static final String URL = "https://chalkak.vercel.app/event";

    @Autowired
    private AdminBannerService bannerService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        clean();
        jdbcTemplate.update(
                """
                        INSERT INTO admins (id, username, password, created_at, updated_at)
                        VALUES (?, 'banner-transaction-manager', 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                ADMIN_ID);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    @DisplayName("앞뒤 공백만 다른 동일 URL의 중복 등록을 거절하고 기존 배너와 감사 기록을 유지한다")
    void createBanner_duplicateUrl_rollsBackSecondRegistration() {
        // Given
        createBanner(ADMIN_ID, URL);

        // When & Then
        assertThatThrownBy(() -> createBanner(ADMIN_ID, " " + URL + " "))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
                    assertThat(exception.getMessage()).isEqualTo("동일한 웹뷰 URL의 배너가 이미 존재합니다.");
                });
        assertThat(countBanners()).isEqualTo(1);
        assertThat(countAudits()).isEqualTo(1);
    }

    @Test
    @DisplayName("동일 URL을 동시에 등록하면 하나만 성공하고 감사 로그도 한 건만 남는다")
    void createBanner_concurrentDuplicateUrl_persistsOnlyOne() throws Exception {
        // Given
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<String>> results = List.of(
                    executor.submit(() -> createAfterBarrier(ready, start)),
                    executor.submit(() -> createAfterBarrier(ready, start)));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            // When
            start.countDown();
            List<String> outcomes = List.of(results.get(0).get(10, TimeUnit.SECONDS),
                    results.get(1).get(10, TimeUnit.SECONDS));

            // Then
            assertThat(outcomes).containsExactlyInAnyOrder("created", "duplicate");
            assertThat(countBanners()).isEqualTo(1);
            assertThat(countAudits()).isEqualTo(1);
        } finally {
            start.countDown();
        }
    }

    @Test
    @DisplayName("감사 기록의 관리자 FK 검증이 실패하면 배너 등록도 롤백된다")
    void createBanner_auditFailure_rollsBackBanner() {
        // When & Then
        assertThatThrownBy(() -> createBanner(UUID.randomUUID(), URL))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(countBanners()).isZero();
        assertThat(countAudits()).isZero();
    }

    private String createAfterBarrier(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 등록 시작 대기 시간 초과");
        }
        try {
            createBanner(ADMIN_ID, URL);
            return "created";
        } catch (BusinessException exception) {
            assertThat(exception.getMessage()).isEqualTo("동일한 웹뷰 URL의 배너가 이미 존재합니다.");
            return "duplicate";
        }
    }

    private void createBanner(UUID adminId, String url) {
        bannerService.createBanner(adminId, "제목", "부제목", url,
                "https://cdn.example.com/banner.png");
    }

    private int countBanners() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM banners", Integer.class);
    }

    private int countAudits() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs WHERE target_type = 'BANNER'",
                Integer.class);
    }

    private void clean() {
        new DatabaseCleaner(jdbcTemplate).clean();
        jdbcTemplate.update("DELETE FROM admin_audit_logs WHERE actor_admin_id = ?", ADMIN_ID);
        jdbcTemplate.update("DELETE FROM admins WHERE id = ?", ADMIN_ID);
    }
}
