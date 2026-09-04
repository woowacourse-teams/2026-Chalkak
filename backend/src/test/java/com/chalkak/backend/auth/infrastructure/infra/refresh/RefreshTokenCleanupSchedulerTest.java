package com.chalkak.backend.auth.infrastructure.infra.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.domain.AdminRefreshToken;
import com.chalkak.backend.admin.repository.AdminRefreshTokenRepository;
import com.chalkak.backend.admin.repository.AdminRepository;
import com.chalkak.backend.auth.domain.RefreshToken;
import com.chalkak.backend.auth.domain.UserRefreshToken;
import com.chalkak.backend.auth.repository.UserRefreshTokenRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RefreshTokenCleanupSchedulerTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");
    private static final Instant REVOKED_THRESHOLD = NOW.minus(Duration.ofDays(7));
    private static final Instant FAR_FUTURE = NOW.plus(Duration.ofDays(30));
    private static final String PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Autowired
    private RefreshTokenCleanupScheduler refreshTokenCleanupScheduler;

    @Autowired
    private UserRefreshTokenRepository userRefreshTokenRepository;

    @Autowired
    private AdminRefreshTokenRepository adminRefreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminRepository adminRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(NOW);
        given(clock.getZone()).willReturn(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("절대 만료가 지난 회원 토큰과 폐기된 지 오래된 회원 토큰만 지운다")
    void deleteUnusableTokens_unusableUserTokens_deletesOnlyThose() {
        // given
        User user = saveUser();
        String absolutelyExpired = saveUserToken(user, NOW.minusSeconds(1), null, null);
        String longRevoked = saveUserToken(
                user,
                FAR_FUTURE,
                null,
                REVOKED_THRESHOLD.minusSeconds(1));
        String live = saveUserToken(user, FAR_FUTURE, null, null);
        String justRevoked = saveUserToken(user, FAR_FUTURE, null, NOW.minusSeconds(1));

        // when
        refreshTokenCleanupScheduler.deleteUnusableTokens();

        // then
        flushAndClear();
        assertThat(existsUserToken(absolutelyExpired)).isFalse();
        assertThat(existsUserToken(longRevoked)).isFalse();
        assertThat(existsUserToken(live)).isTrue();
        assertThat(existsUserToken(justRevoked)).isTrue();
    }

    @Test
    @DisplayName("회전됐지만 절대 만료 전인 회원 토큰은 비활동 만료가 지나도 남긴다")
    void deleteUnusableTokens_rotatedButNotAbsolutelyExpired_keepsReuseEvidence() {
        // given
        User user = saveUser();
        String rotated = saveUserToken(user, FAR_FUTURE, NOW.minus(Duration.ofDays(40)), null);

        // when
        refreshTokenCleanupScheduler.deleteUnusableTokens();

        // then
        flushAndClear();
        assertThat(existsUserToken(rotated)).isTrue();
    }

    @Test
    @DisplayName("절대 만료와 폐기 유예의 경계에 정확히 걸친 회원 토큰은 남긴다")
    void deleteUnusableTokens_tokensExactlyOnBoundary_areKept() {
        // given
        User user = saveUser();
        String expiringNow = saveUserToken(user, NOW, null, null);
        String revokedOnThreshold = saveUserToken(user, FAR_FUTURE, null, REVOKED_THRESHOLD);

        // when
        refreshTokenCleanupScheduler.deleteUnusableTokens();

        // then
        flushAndClear();
        assertThat(existsUserToken(expiringNow)).isTrue();
        assertThat(existsUserToken(revokedOnThreshold)).isTrue();
    }

    @Test
    @DisplayName("관리자 토큰도 같은 기준으로 지운다")
    void deleteUnusableTokens_unusableAdminTokens_deletesOnlyThose() {
        // given
        Admin admin = saveAdmin();
        String absolutelyExpired = saveAdminToken(admin, NOW.minusSeconds(1), null, null);
        String longRevoked = saveAdminToken(
                admin,
                FAR_FUTURE,
                null,
                REVOKED_THRESHOLD.minusSeconds(1));
        String live = saveAdminToken(admin, FAR_FUTURE, null, null);
        String justRevoked = saveAdminToken(admin, FAR_FUTURE, null, NOW.minusSeconds(1));
        String rotated = saveAdminToken(admin, FAR_FUTURE, NOW.minus(Duration.ofDays(40)), null);

        // when
        refreshTokenCleanupScheduler.deleteUnusableTokens();

        // then
        flushAndClear();
        assertThat(existsAdminToken(absolutelyExpired)).isFalse();
        assertThat(existsAdminToken(longRevoked)).isFalse();
        assertThat(existsAdminToken(live)).isTrue();
        assertThat(existsAdminToken(justRevoked)).isTrue();
        assertThat(existsAdminToken(rotated)).isTrue();
    }

    private String saveUserToken(
            User user,
            Instant absoluteExpiresAt,
            Instant rotatedAt,
            Instant revokedAt
    ) {
        String tokenHash = createTokenHash();
        UserRefreshToken refreshToken = UserRefreshToken.create(
                user,
                UUID.randomUUID(),
                tokenHash,
                expiresAt(absoluteExpiresAt, rotatedAt),
                absoluteExpiresAt);
        applyLifecycle(refreshToken, rotatedAt, revokedAt);
        userRefreshTokenRepository.save(refreshToken);
        flushAndClear();
        return tokenHash;
    }

    private String saveAdminToken(
            Admin admin,
            Instant absoluteExpiresAt,
            Instant rotatedAt,
            Instant revokedAt
    ) {
        String tokenHash = createTokenHash();
        AdminRefreshToken refreshToken = AdminRefreshToken.create(
                admin,
                UUID.randomUUID(),
                tokenHash,
                expiresAt(absoluteExpiresAt, rotatedAt),
                absoluteExpiresAt);
        applyLifecycle(refreshToken, rotatedAt, revokedAt);
        adminRefreshTokenRepository.save(refreshToken);
        flushAndClear();
        return tokenHash;
    }

    /** 회전된 토큰은 비활동 만료까지 지난 상태를 재현해야 절대 만료만으로 남는지 확인할 수 있다. */
    private Instant expiresAt(Instant absoluteExpiresAt, Instant rotatedAt) {
        if (rotatedAt == null) {
            return absoluteExpiresAt;
        }
        return rotatedAt;
    }

    private void applyLifecycle(
            RefreshToken refreshToken,
            Instant rotatedAt,
            Instant revokedAt
    ) {
        if (rotatedAt != null) {
            refreshToken.rotate(rotatedAt);
        }
        if (revokedAt != null) {
            refreshToken.revoke(revokedAt);
        }
    }

    private boolean existsUserToken(String tokenHash) {
        return userRefreshTokenRepository.findByTokenHash(tokenHash).isPresent();
    }

    private boolean existsAdminToken(String tokenHash) {
        return adminRefreshTokenRepository.findByTokenHash(tokenHash).isPresent();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private String createTokenHash() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private User saveUser() {
        String unique = UUID.randomUUID().toString();
        User user = userRepository.save(User.create(
                null,
                new SignatureStorageKeys(
                        "signatures/original/" + unique,
                        "signatures/thumbnail/" + unique)));
        flushAndClear();
        return user;
    }

    private Admin saveAdmin() {
        Admin admin = adminRepository.save(Admin.create(
                "cleanup-operator-" + UUID.randomUUID(),
                PASSWORD_HASH));
        flushAndClear();
        return admin;
    }
}
