package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.auth.service.SocialIdentityFingerprintEncoder;
import com.chalkak.backend.auth.service.UserRefreshTokenService;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.notification.repository.PushDeviceRepository;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import com.chalkak.backend.user.service.UserWithdrawalService;
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

class PushDeviceWithdrawalTransactionTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");

    @Autowired
    private UserWithdrawalService userWithdrawalService;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private SocialIdentityFingerprintEncoder fingerprintEncoder;

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
        jdbcTemplate.execute("DROP TABLE IF EXISTS push_device_withdrawal_guard");
        new DatabaseCleaner(jdbcTemplate).clean();
    }

    @Test
    @DisplayName("기기 행 삭제에 실패하면 회원·소셜 연결·RT 폐기도 함께 롤백한다")
    void withdraw_deviceDeletionFails_rollsBackUserSocialAccountAndRefreshToken() {
        // Given
        User user = userRepository.save(UserFixture.create());
        SocialAccount account = socialAccountRepository.save(SocialAccount.create(
                user, SocialProvider.GOOGLE,
                fingerprintEncoder.encode(SocialProvider.GOOGLE, "rollback-subject")));
        IssuedRefreshToken issued = userRefreshTokenService.issue(user);
        pushDeviceService.register(user.getId(), issued.sessionId(), "rollback-token");
        blockDeviceDeletion(issued.sessionId());

        // When & Then
        assertThatThrownBy(() -> userWithdrawalService.withdraw(user.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(userRepository.findActiveById(user.getId())).isPresent();
        assertThat(socialAccountRepository.findByUserId(user.getId()).orElseThrow().getId())
                .isEqualTo(account.getId());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_refresh_tokens WHERE session_id = ? AND revoked_at IS NULL",
                Integer.class, issued.sessionId())).isEqualTo(1);
        assertThat(pushDeviceRepository.findBySessionId(issued.sessionId()).orElseThrow()
                .getFcmToken())
                .isEqualTo("rollback-token");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("기존 연결을 옮기는 기기 등록과 탈퇴가 겹쳐도 두 로그인 기기와 RT가 모두 해제된다")
    void registerAndWithdraw_concurrentRequests_leavesNoActiveDevice(boolean withdrawalFirst)
            throws Exception {
        // Given
        User user = userRepository.save(UserFixture.create());
        IssuedRefreshToken issued = userRefreshTokenService.issue(user);
        pushDeviceService.register(user.getId(), issued.sessionId(), "initial-token");
        IssuedRefreshToken nextLogin = userRefreshTokenService.issue(user);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch firstCompleted = new CountDownLatch(1);
        CountDownLatch firstCanCommit = new CountDownLatch(1);
        CompletableFuture<Integer> secondBackendPid = new CompletableFuture<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> transaction.executeWithoutResult(status -> {
                if (withdrawalFirst) {
                    userWithdrawalService.withdraw(user.getId());
                }
                if (!withdrawalFirst) {
                    pushDeviceService.register(user.getId(), nextLogin.sessionId(),
                            "initial-token");
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
                        if (withdrawalFirst) {
                            pushDeviceService.register(user.getId(), nextLogin.sessionId(),
                                    "initial-token");
                        }
                        if (!withdrawalFirst) {
                            userWithdrawalService.withdraw(user.getId());
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
            assertThat(result).isEqualTo(withdrawalFirst ? "UNAUTHORIZED" : "SUCCESS");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                    Integer.class, user.getId())).isZero();
            assertThat(userRepository.findActiveById(user.getId())).isEmpty();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM push_devices WHERE user_id = ?",
                    Integer.class, user.getId())).isZero();
        } finally {
            firstCanCommit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void blockDeviceDeletion(UUID sessionId) {
        jdbcTemplate.execute("""
                CREATE TABLE push_device_withdrawal_guard (
                    device_id UUID NOT NULL REFERENCES push_devices(id) ON DELETE RESTRICT
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO push_device_withdrawal_guard (device_id)
                SELECT id FROM push_devices WHERE session_id = ?
                """, sessionId);
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
