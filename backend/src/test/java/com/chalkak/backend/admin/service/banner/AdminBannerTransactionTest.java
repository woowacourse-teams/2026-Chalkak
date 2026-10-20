package com.chalkak.backend.admin.service.banner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.admin.service.audit.AdminAuditLogService;
import com.chalkak.backend.banner.domain.Banner;
import com.chalkak.backend.banner.repository.BannerPage;
import com.chalkak.backend.banner.repository.BannerRepository;
import com.chalkak.backend.banner.service.BannerService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
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
    private AdminBannerService adminBannerService;

    @Autowired
    private BannerService bannerService;

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
                auditLogService, Clock.systemUTC());
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

    @Test
    @DisplayName("동시 삭제는 같은 행 잠금으로 직렬화하고 최초 시각·감사 한 건을 유지한다")
    void deleteBanner_concurrent_preservesFirstAudit() throws Exception {
        // Given
        createBanner(ADMIN_ID, URL);
        UUID id = jdbcTemplate.queryForObject("SELECT id FROM banners", UUID.class);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondAttempted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        BannerRepository lockingRepository = new BannerRepository() {
            @Override
            public Optional<Banner> findByIdForUpdate(UUID bannerId) {
                int attempt = reads.incrementAndGet();
                if (attempt == 2) {
                    secondAttempted.countDown();
                }
                Optional<Banner> result = bannerRepository.findByIdForUpdate(bannerId);
                if (attempt == 1) {
                    firstLocked.countDown();
                    await(releaseFirst);
                }
                return result;
            }

            @Override
            public boolean existsByWebViewUrl(String url) {
                return bannerRepository.existsByWebViewUrl(url);
            }

            @Override
            public Banner save(Banner banner) {
                return bannerRepository.save(banner);
            }

            @Override
            public BannerPage findBanners(int page, int pageSize) {
                return bannerRepository.findBanners(page, pageSize);
            }
        };
        AdminBannerService service = new AdminBannerService(lockingRepository, auditLogService,
                Clock.fixed(java.time.Instant.parse("2026-10-19T01:00:00Z"),
                        java.time.ZoneOffset.UTC));
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> service.deleteBanner(id, ADMIN_ID)));
            try {
                await(firstLocked);
                Future<?> second = executor.submit(() -> new TransactionTemplate(transactionManager)
                        .executeWithoutResult(
                                status -> service.deleteBanner(id, ADMIN_ID)));
                await(secondAttempted);
                // 첫 트랜잭션이 잠금을 유지하는 동안 두 번째 삭제는 완료될 수 없다.
                assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
                releaseFirst.countDown();
                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);
            } finally {
                releaseFirst.countDown();
            }
        }

        // Then
        assertThat(reads.get()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admin_audit_logs WHERE action = 'BANNER_DELETED'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT reason FROM admin_audit_logs WHERE action = 'BANNER_DELETED'
                """, String.class)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT deleted_at FROM banners WHERE id = ?",
                java.time.OffsetDateTime.class, id).toInstant())
                .isEqualTo(java.time.Instant.parse("2026-10-19T01:00:00Z"));
    }

    @Test
    @DisplayName("삭제 감사 기록 실패 시 삭제 시각도 롤백된다")
    void deleteBanner_auditFailure_rollsBackDeletion() {
        // Given
        createBanner(ADMIN_ID, URL);
        UUID id = jdbcTemplate.queryForObject("SELECT id FROM banners", UUID.class);

        // When & Then
        assertThatThrownBy(() -> adminBannerService.deleteBanner(id, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT deleted_at FROM banners WHERE id = ?",
                java.time.OffsetDateTime.class, id)).isNull();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admin_audit_logs WHERE action = 'BANNER_DELETED'
                """, Integer.class)).isZero();
        assertThat(bannerService.getBanners(1, 20).banners()).hasSize(1);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("삭제 동기화 대기 시간 초과");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("삭제 동기화 중 중단", exception);
        }
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
        adminBannerService.createBanner(adminId, "제목", "부제목", url,
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
        public Optional<Banner> findByIdForUpdate(UUID bannerId) {
            return delegate.findByIdForUpdate(bannerId);
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
