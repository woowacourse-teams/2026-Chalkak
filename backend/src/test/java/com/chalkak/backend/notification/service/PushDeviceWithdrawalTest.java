package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import com.chalkak.backend.auth.service.UserRefreshTokenService;
import com.chalkak.backend.notification.domain.PushDevice;
import com.chalkak.backend.notification.repository.PushDeviceRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import com.chalkak.backend.user.service.UserWithdrawalService;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PushDeviceWithdrawalTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");

    @Autowired
    private UserWithdrawalService userWithdrawalService;

    @Autowired
    private UserRefreshTokenService userRefreshTokenService;

    @Autowired
    private PushDeviceService pushDeviceService;

    @Autowired
    private PushDeviceRepository pushDeviceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(NOW);
        given(clock.getZone()).willReturn(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("탈퇴하면 모든 로그인 기기의 토큰을 제거하고 다른 회원의 기기는 유지한다")
    void withdraw_multipleDevices_disablesAllOwnedDevicesOnly() {
        // Given
        User user = userRepository.save(UserFixture.create());
        IssuedRefreshToken first = register(user, "first-token");
        IssuedRefreshToken second = register(user, "second-token");
        User otherUser = userRepository.save(UserFixture.create());
        IssuedRefreshToken other = register(otherUser, "other-token");
        flushAndClear();

        // When
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // Then
        assertDisabled(first.sessionId(), NOW);
        assertDisabled(second.sessionId(), NOW);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_refresh_tokens
                WHERE user_id = ? AND revoked_at IS NULL
                """, Integer.class, user.getId())).isZero();
        assertThat(userRepository.findActiveById(user.getId())).isEmpty();
        PushDevice otherDevice = pushDeviceRepository.findBySessionId(other.sessionId())
                .orElseThrow();
        assertThat(otherDevice.getDisabledAt()).isNull();
        assertThat(otherDevice.getFcmToken()).isEqualTo("other-token");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_refresh_tokens
                WHERE user_id = ? AND revoked_at IS NULL
                """, Integer.class, otherUser.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("기기 등록이 없는 회원도 탈퇴하고 RT를 폐기한다")
    void withdraw_noDevice_revokesRefreshToken() {
        // Given
        User user = userRepository.save(UserFixture.create());
        IssuedRefreshToken issued = userRefreshTokenService.issue(user);
        flushAndClear();

        // When
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // Then
        assertThat(userRepository.findActiveById(user.getId())).isEmpty();
        assertThat(pushDeviceRepository.findBySessionId(issued.sessionId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_refresh_tokens
                WHERE session_id = ? AND revoked_at IS NOT NULL
                """, Integer.class, issued.sessionId())).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 폐기된 RT의 로그인에 남은 활성 기기도 탈퇴 시 해제한다")
    void withdraw_revokedRefreshToken_disablesRemainingDevice() {
        // Given
        User user = userRepository.save(UserFixture.create());
        IssuedRefreshToken issued = register(user, "remaining-token");
        flushAndClear();
        jdbcTemplate.update("UPDATE user_refresh_tokens SET revoked_at = ? WHERE session_id = ?",
                java.sql.Timestamp.from(NOW.minusSeconds(60)), issued.sessionId());

        // When
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // Then
        assertDisabled(issued.sessionId(), NOW);
        assertThat(userRepository.findActiveById(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("다른 회원에게 옮겨진 토큰은 유지하고 기존 비활성화 시각은 바꾸지 않는다")
    void withdraw_transferredToken_preservesNewOwnerAndPreviousDisabledAt() {
        // Given
        User user = userRepository.save(UserFixture.create());
        IssuedRefreshToken original = register(user, "shared-token");
        User otherUser = userRepository.save(UserFixture.create());
        Instant transferredAt = NOW.plusSeconds(60);
        given(clock.instant()).willReturn(transferredAt);
        IssuedRefreshToken transferred = register(otherUser, "shared-token");
        flushAndClear();
        given(clock.instant()).willReturn(NOW.plusSeconds(120));

        // When
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // Then
        assertDisabled(original.sessionId(), transferredAt);
        PushDevice current = pushDeviceRepository.findBySessionId(transferred.sessionId())
                .orElseThrow();
        assertThat(current.getDisabledAt()).isNull();
        assertThat(current.getFcmToken()).isEqualTo("shared-token");
    }

    @Test
    @DisplayName("정지 회원이 탈퇴해도 연결된 모든 푸시 기기의 토큰을 제거한다")
    void withdraw_bannedUser_disablesDevice() {
        // Given
        User user = userRepository.save(UserFixture.createBanned(null));
        IssuedRefreshToken issued = register(user, "banned-token");
        flushAndClear();

        // When
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // Then
        assertDisabled(issued.sessionId(), NOW);
        assertThat(userRepository.findActiveById(user.getId())).isEmpty();
    }

    private IssuedRefreshToken register(User user, String token) {
        IssuedRefreshToken issued = userRefreshTokenService.issue(user);
        pushDeviceService.register(user.getId(), issued.sessionId(), token);
        return issued;
    }

    private void assertDisabled(UUID sessionId, Instant disabledAt) {
        PushDevice device = pushDeviceRepository.findBySessionId(sessionId).orElseThrow();
        assertThat(device.getDisabledAt()).isEqualTo(disabledAt);
        assertThat(device.getUpdatedAt()).isEqualTo(disabledAt);
        assertThat(device.getFcmToken()).isNull();
        assertThat(device.getFcmTokenHash()).isNull();
        assertThat(device.getRegisteredAt()).isEqualTo(NOW);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
