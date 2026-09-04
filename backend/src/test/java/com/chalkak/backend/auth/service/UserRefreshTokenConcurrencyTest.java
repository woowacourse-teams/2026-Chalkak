package com.chalkak.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.exception.BaseException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 회전은 다른 스레드의 트랜잭션이 실제로 커밋돼야 검증되므로 {@code @Transactional} 격리를 쓰지 않고
 * 직접 정리한다.
 */
class UserRefreshTokenConcurrencyTest extends IntegrationTestSupport {

    private static final String SUCCESS = "SUCCESS";
    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");
    private static final UUID USER_ID =
            UUID.fromString("0198fd30-0000-7000-8000-000000000001");
    private static final UUID SESSION_ID =
            UUID.fromString("0198fd30-0000-7000-8000-000000000002");
    private static final String PRESENTED_TOKEN = "concurrent-refresh-token";

    @Autowired
    private UserRefreshTokenService userRefreshTokenService;

    @Autowired
    private RefreshTokenHasher refreshTokenHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        cleanUp();
        given(clock.instant()).willReturn(NOW);
        given(clock.getZone()).willReturn(ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'concurrent-refresh@example.com', 'ACTIVE',
                    'signatures/concurrent-refresh', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, USER_ID);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("같은 토큰으로 동시에 재발급하면 하나만 성공하고 계보 전체가 폐기된다")
    void refresh_concurrentRotationOfSameToken_succeedsForOneCallerAndRevokesLineage()
            throws Exception {
        // given
        insertRefreshToken(PRESENTED_TOKEN, null);

        // when
        List<RefreshAttempt> attempts = runConcurrently();

        // then
        // 뒤늦게 잠금을 얻은 쪽은 이미 회전된 토큰을 제시한 셈이라 탈취로 판정된다.
        assertThat(attempts)
                .extracting(RefreshAttempt::outcome)
                .containsExactlyInAnyOrder(
                        SUCCESS,
                        ErrorCode.REAUTHENTICATION_REQUIRED.name());
        assertThat(countLiveTokens()).isZero();
        assertThat(countRevokedTokens()).isEqualTo(2);
    }

    @Test
    @DisplayName("회전된 토큰을 동시에 재사용하면 둘 다 거절되고 계보가 폐기된다")
    void refresh_concurrentReuseOfRotatedToken_revokesLineageForBothCallers() throws Exception {
        // given
        insertRefreshToken(PRESENTED_TOKEN, NOW.minus(Duration.ofSeconds(60)));
        insertRefreshToken("successor-refresh-token", null);

        // when
        List<RefreshAttempt> attempts = runConcurrently();

        // then
        assertThat(attempts)
                .extracting(RefreshAttempt::outcome)
                .containsExactly(
                        ErrorCode.REAUTHENTICATION_REQUIRED.name(),
                        ErrorCode.REAUTHENTICATION_REQUIRED.name());
        assertThat(countLiveTokens()).isZero();
        assertThat(countRevokedTokens()).isEqualTo(2);
    }

    private List<RefreshAttempt> runConcurrently() throws Exception {
        Callable<TokenRefreshResult> refresh =
                () -> userRefreshTokenService.refresh(PRESENTED_TOKEN);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<RefreshAttempt> first = executor.submit(() -> attempt(barrier, refresh));
            Future<RefreshAttempt> second = executor.submit(() -> attempt(barrier, refresh));
            return List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );
        }
    }

    private RefreshAttempt attempt(
            CyclicBarrier barrier,
            Callable<TokenRefreshResult> action
    ) {
        try {
            barrier.await();
            return new RefreshAttempt(SUCCESS, action.call().refreshToken().value());
        } catch (BaseException exception) {
            return new RefreshAttempt(exception.getErrorCode().name(), null);
        } catch (Exception exception) {
            return new RefreshAttempt(
                    "UNEXPECTED:" + exception.getClass().getSimpleName(),
                    null
            );
        }
    }

    private void insertRefreshToken(String token, Instant rotatedAt) {
        jdbcTemplate.update("""
                        INSERT INTO user_refresh_tokens (
                            user_id, session_id, token_hash,
                            expires_at, absolute_expires_at, rotated_at
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """,
                USER_ID,
                SESSION_ID,
                refreshTokenHasher.encode(token),
                Timestamp.from(NOW.plus(Duration.ofDays(30))),
                Timestamp.from(NOW.plus(Duration.ofDays(90))),
                (rotatedAt == null) ? null : Timestamp.from(rotatedAt));
    }

    private int countLiveTokens() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_refresh_tokens
                WHERE session_id = ? AND revoked_at IS NULL
                """, Integer.class, SESSION_ID);
    }

    private int countRevokedTokens() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_refresh_tokens
                WHERE session_id = ? AND revoked_at IS NOT NULL
                """, Integer.class, SESSION_ID);
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM user_refresh_tokens WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", USER_ID);
    }

    private record RefreshAttempt(
            String outcome,
            String issuedToken
    ) {
    }
}
