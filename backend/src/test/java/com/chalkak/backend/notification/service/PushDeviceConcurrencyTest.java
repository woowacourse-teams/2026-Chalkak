package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.auth.service.UserRefreshTokenService;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
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

class PushDeviceConcurrencyTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");

    @Autowired
    private PushDeviceService pushDeviceService;

    @Autowired
    private UserRefreshTokenService userRefreshTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        new DatabaseCleaner(jdbcTemplate).clean();
    }

    @Test
    @DisplayName("같은 토큰을 두 로그인이 동시에 등록해도 둘 다 처리되고 활성 연결은 하나만 남는다")
    void register_sameTokenConcurrently_keepsSingleActiveBinding() throws Exception {
        // given
        Login first = login();
        Login second = login();

        // when
        runConcurrently(registering(first, "shared-token"), registering(second, "shared-token"));

        // then
        assertThat(countDevices()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM push_devices
                WHERE disabled_at IS NULL AND fcm_token = 'shared-token'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM push_devices
                WHERE disabled_at IS NOT NULL AND fcm_token IS NULL AND fcm_token_hash IS NULL
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 로그인의 서로 다른 토큰 등록이 겹쳐도 하나의 기기 행만 남는다")
    void register_sameSessionConcurrently_keepsSingleDeviceRow() throws Exception {
        // given
        Login login = login();

        // when
        runConcurrently(registering(login, "first-token"), registering(login, "second-token"));

        // then
        assertThat(countDevices()).isEqualTo(1);
        assertThat(findActiveToken(login.sessionId())).isIn("first-token", "second-token");
    }

    @Test
    @DisplayName("두 로그인이 서로의 토큰으로 동시에 바꾸어도 교착 없이 두 등록을 처리한다")
    void register_swappingTokensConcurrently_completesWithoutDeadlock() throws Exception {
        // given
        Login first = login();
        Login second = login();
        registering(first, "first-token").run();
        registering(second, "second-token").run();

        // when
        runConcurrently(registering(first, "second-token"), registering(second, "first-token"));

        // then
        assertThat(findActiveToken(first.sessionId())).isEqualTo("second-token");
        assertThat(findActiveToken(second.sessionId())).isEqualTo("first-token");
        assertThat(countDevices()).isEqualTo(2);
    }

    private Login login() {
        User user = userRepository.save(UserFixture.create());
        UUID sessionId = userRefreshTokenService.issue(user).sessionId();
        return new Login(user.getId(), sessionId);
    }

    private Runnable registering(Login login, String token) {
        return () -> pushDeviceService.register(login.userId(), login.sessionId(), token);
    }

    private void runConcurrently(Runnable first, Runnable second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> firstResult = executor.submit(() -> {
                barrier.await();
                first.run();
                return null;
            });
            Future<?> secondResult = executor.submit(() -> {
                barrier.await();
                second.run();
                return null;
            });
            firstResult.get(10, TimeUnit.SECONDS);
            secondResult.get(10, TimeUnit.SECONDS);
        }
    }

    private String findActiveToken(UUID sessionId) {
        return jdbcTemplate.queryForObject("""
                SELECT fcm_token FROM push_devices WHERE session_id = ? AND disabled_at IS NULL
                """, String.class, sessionId);
    }

    private int countDevices() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM push_devices", Integer.class);
    }

    private record Login(UUID userId, UUID sessionId) {
    }
}
