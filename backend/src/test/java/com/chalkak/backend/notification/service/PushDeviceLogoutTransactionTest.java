package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import com.chalkak.backend.auth.service.UserRefreshTokenService;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.notification.repository.PushDeviceRepository;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PushDeviceLogoutTransactionTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");

    @Autowired
    private PushDeviceService pushDeviceService;

    @Autowired
    private UserRefreshTokenService userRefreshTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PushDeviceRepository pushDeviceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbcTemplate).clean();
        given(clock.instant()).willReturn(NOW);
        given(clock.getZone()).willReturn(ZoneOffset.UTC);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("ALTER TABLE push_devices DROP CONSTRAINT IF EXISTS ck_logout_test");
        new DatabaseCleaner(jdbcTemplate).clean();
    }

    @Test
    @DisplayName("기기 비활성화 저장에 실패하면 RT 폐기도 함께 롤백한다")
    void logout_deviceUpdateFails_rollsBackRefreshTokenRevocation() {
        // Given
        User user = userRepository.save(UserFixture.create());
        IssuedRefreshToken issued = userRefreshTokenService.issue(user);
        pushDeviceService.register(user.getId(), issued.sessionId(), "rollback-token");
        jdbcTemplate.execute(
                "ALTER TABLE push_devices ADD CONSTRAINT ck_logout_test CHECK (disabled_at IS NULL)");

        // When & Then
        assertThatThrownBy(() -> userRefreshTokenService.logout(issued.value()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_refresh_tokens WHERE session_id = ? AND revoked_at IS NULL",
                Integer.class, issued.sessionId())).isEqualTo(1);
        assertThat(pushDeviceRepository.findBySessionId(issued.sessionId()).orElseThrow()
                .getFcmToken())
                .isEqualTo("rollback-token");
        assertThat(pushDeviceRepository.findBySessionId(issued.sessionId()).orElseThrow()
                .getDisabledAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("기기 등록과 로그아웃이 겹쳐도 로그아웃이 커밋되면 활성 기기와 유효한 RT가 남지 않는다")
    void registerAndLogout_concurrentRequests_leavesNoActiveDevice(boolean logoutFirst)
            throws Exception {
        // Given
        User user = userRepository.save(UserFixture.create());
        IssuedRefreshToken issued = userRefreshTokenService.issue(user);
        pushDeviceService.register(user.getId(), issued.sessionId(), "initial-token");
        UUID deviceId = pushDeviceRepository.findBySessionId(issued.sessionId()).orElseThrow()
                .getId();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch firstCompleted = new CountDownLatch(1);
        CountDownLatch firstCanCommit = new CountDownLatch(1);
        CompletableFuture<Integer> secondBackendPid = new CompletableFuture<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> transaction.executeWithoutResult(status -> {
                if (logoutFirst) {
                    userRefreshTokenService.logout(issued.value());
                }
                if (!logoutFirst) {
                    pushDeviceService.register(user.getId(), issued.sessionId(), "updated-token");
                }
                firstCompleted.countDown();
                awaitCommit(firstCanCommit);
            }));
            assertThat(firstCompleted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<String> second = executor.submit(() -> {
                try {
                    transaction.executeWithoutResult(status -> {
                        secondBackendPid.complete(jdbcTemplate
                                .queryForObject("SELECT pg_backend_pid()", Integer.class));
                        if (logoutFirst) {
                            pushDeviceService.register(user.getId(), issued.sessionId(),
                                    "updated-token");
                        }
                        if (!logoutFirst) {
                            userRefreshTokenService.logout(issued.value());
                        }
                    });
                    return "SUCCESS";
                } catch (UnauthorizedException exception) {
                    return exception.getErrorCode().name();
                }
            });

            // When
            awaitBlocked(secondBackendPid.get(5, TimeUnit.SECONDS));
            assertThat(second.isDone()).isFalse();
            firstCanCommit.countDown();
            first.get(5, TimeUnit.SECONDS);
            String result = second.get(5, TimeUnit.SECONDS);

            // Then
            assertThat(result).isEqualTo(logoutFirst ? "REAUTHENTICATION_REQUIRED" : "SUCCESS");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_refresh_tokens WHERE session_id = ? AND revoked_at IS NULL",
                    Integer.class, issued.sessionId())).isZero();
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM push_devices
                    WHERE id = ? AND disabled_at IS NOT NULL
                        AND user_id IS NULL AND session_id IS NULL
                        AND fcm_token IS NULL AND fcm_token_hash IS NULL
                    """, Integer.class, deviceId)).isEqualTo(1);
        } finally {
            firstCanCommit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void awaitCommit(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("첫 번째 요청의 커밋 허용을 기다리지 못했습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 대기가 중단되었습니다.", exception);
        }
    }

    private void awaitBlocked(int backendPid) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Boolean blocked = jdbcTemplate.queryForObject(
                    "SELECT cardinality(pg_blocking_pids(?)) > 0", Boolean.class, backendPid);
            if (Boolean.TRUE.equals(blocked)) {
                return;
            }
            Thread.sleep(20);
        }
        throw new IllegalStateException("두 번째 요청이 로그인 잠금을 기다리지 않았습니다.");
    }
}
