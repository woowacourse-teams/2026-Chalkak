package com.chalkak.backend.admin.service.banner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.admin.service.audit.AdminAuditLogService;
import com.chalkak.backend.banner.domain.Banner;
import com.chalkak.backend.banner.repository.BannerPage;
import com.chalkak.backend.banner.repository.BannerRepository;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AdminBannerTransactionTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000676");
    private static final String URL = "https://chalkak.vercel.app/event";

    @Autowired
    private AdminBannerService bannerService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BannerRepository bannerRepository;

    @Autowired
    private AdminAuditLogService auditLogService;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
    @DisplayName("두 요청이 사전 검사를 통과한 뒤 경쟁하면 DB 중복을 업무 예외로 변환하고 한 건만 저장한다")
    void createBanner_concurrentDuplicateAfterPrecheck_persistsOnlyOne() throws Exception {
        // Given
        AtomicInteger saveAttempts = new AtomicInteger();
        AtomicInteger duplicateSaveFailures = new AtomicInteger();
        BannerRepository racingRepository = new RacingBannerRepository(bannerRepository,
                new CountDownLatch(2), saveAttempts, duplicateSaveFailures);
        AdminBannerService racingService = new AdminBannerService(racingRepository,
                auditLogService);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            // When
            List<Future<String>> results = List.of(
                    executor.submit(() -> createInTransaction(racingService)),
                    executor.submit(() -> createInTransaction(racingService)));
            List<String> outcomes = List.of(results.get(0).get(15, TimeUnit.SECONDS),
                    results.get(1).get(15, TimeUnit.SECONDS));

            // Then
            assertThat(outcomes).containsExactlyInAnyOrder("created", "duplicate");
            assertThat(saveAttempts.get()).isEqualTo(2);
            assertThat(duplicateSaveFailures.get()).isEqualTo(1);
            assertThat(countBanners()).isEqualTo(1);
            assertThat(countAudits()).isEqualTo(1);
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

    private String createInTransaction(AdminBannerService service) {
        try {
            new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> service.createBanner(ADMIN_ID, "제목", "부제목", URL,
                            "https://cdn.example.com/banner.png"));
            return "created";
        } catch (BusinessException exception) {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
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
    private record RacingBannerRepository(
            BannerRepository delegate,
            CountDownLatch prechecks,
            AtomicInteger saveAttempts,
            AtomicInteger duplicateSaveFailures) implements BannerRepository {

        @Override
        public boolean existsByWebViewUrl(String webViewUrl) {
            boolean exists = delegate.existsByWebViewUrl(webViewUrl);
            assertThat(exists).isFalse();
            prechecks.countDown();
            try {
                if (!prechecks.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("사전 검사 완료 대기 시간 초과");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("사전 검사 대기 중 중단", exception);
            }
            return exists;
        }

        @Override
        public Banner save(Banner banner) {
            saveAttempts.incrementAndGet();
            try {
                return delegate.save(banner);
            } catch (BusinessException exception) {
                duplicateSaveFailures.incrementAndGet();
                throw exception;
            }
        }

        @Override
        public BannerPage findBanners(int page, int pageSize) {
            return delegate.findBanners(page, pageSize);
        }
    }
}
