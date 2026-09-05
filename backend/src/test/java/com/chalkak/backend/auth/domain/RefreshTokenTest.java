package com.chalkak.backend.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.domain.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final String TOKEN_HASH =
            "921c5d35312df654eaa8ec114fd1de5a156cbcc64b23ddb6a709a9423f90c218";
    private static final String SUCCESSOR_TOKEN_HASH =
            "73bce40e3e8b39018d68b14b38454b28892c1b50c93b143ce36b5406949542ba";
    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");

    @Test
    @DisplayName("회전된 적 없는 토큰은 재사용으로 판정하지 않는다")
    void isReused_neverRotated_returnsFalse() {
        // given
        UserRefreshToken refreshToken = createRefreshToken();

        // when
        boolean reused = refreshToken.isReused();

        // then
        assertThat(reused).isFalse();
    }

    @Test
    @DisplayName("회전된 토큰은 회전 직후라도 재사용으로 판정한다")
    void isReused_rotated_returnsTrue() {
        // given
        UserRefreshToken refreshToken = createRefreshToken();
        refreshToken.rotate(NOW);

        // when
        boolean reused = refreshToken.isReused();

        // then
        assertThat(reused).isTrue();
    }

    @Test
    @DisplayName("두 만료 시각이 모두 남아 있으면 만료로 판정하지 않는다")
    void isExpired_beforeBothExpiries_returnsFalse() {
        // given
        UserRefreshToken refreshToken = createRefreshToken(
                NOW.plusSeconds(1),
                NOW.plusSeconds(100));

        // when
        boolean expired = refreshToken.isExpired(NOW);

        // then
        assertThat(expired).isFalse();
    }

    @Test
    @DisplayName("절대 만료가 남아 있어도 비활동 만료가 지나면 만료로 판정한다")
    void isExpired_atInactivityExpiry_returnsTrue() {
        // given
        UserRefreshToken refreshToken = createRefreshToken(
                NOW,
                NOW.plusSeconds(100));

        // when
        boolean expired = refreshToken.isExpired(NOW);

        // then
        assertThat(expired).isTrue();
    }

    @Test
    @DisplayName("비활동 만료가 남아 있어도 절대 만료가 지나면 만료로 판정한다")
    void isExpired_afterAbsoluteExpiry_returnsTrue() {
        // given
        UserRefreshToken refreshToken = createRefreshToken(
                NOW.minusSeconds(10),
                NOW.minusSeconds(5));

        // when
        boolean expired = refreshToken.isExpired(NOW);

        // then
        assertThat(expired).isTrue();
    }

    @Test
    @DisplayName("이미 회전된 토큰을 다시 회전해도 최초 회전 시각을 유지한다")
    void rotate_alreadyRotated_keepsFirstRotatedAt() {
        // given
        UserRefreshToken refreshToken = createRefreshToken();
        refreshToken.rotate(NOW);

        // when
        refreshToken.rotate(NOW.plusSeconds(60));

        // then
        assertThat(refreshToken.getRotatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("이미 폐기된 토큰을 다시 폐기해도 최초 폐기 시각을 유지한다")
    void revoke_alreadyRevoked_keepsFirstRevokedAt() {
        // given
        UserRefreshToken refreshToken = createRefreshToken();
        refreshToken.revoke(NOW);

        // when
        refreshToken.revoke(NOW.plusSeconds(60));

        // then
        assertThat(refreshToken.isRevoked()).isTrue();
        assertThat(refreshToken.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("후속 토큰은 세션과 절대 만료를 물려받고 비활동 만료만 갱신한다")
    void createSuccessor_rotatedToken_inheritsSessionAndAbsoluteExpiry() {
        // given
        UserRefreshToken refreshToken = createRefreshToken(
                NOW.plusSeconds(10),
                NOW.plusSeconds(100));
        Instant nextExpiresAt = NOW.plusSeconds(50);

        // when
        UserRefreshToken successor = refreshToken.createSuccessor(
                SUCCESSOR_TOKEN_HASH,
                nextExpiresAt);

        // then
        assertThat(successor.getSessionId()).isEqualTo(refreshToken.getSessionId());
        assertThat(successor.getAbsoluteExpiresAt())
                .isEqualTo(refreshToken.getAbsoluteExpiresAt());
        assertThat(successor.getUser()).isSameAs(refreshToken.getUser());
        assertThat(successor.getTokenHash()).isEqualTo(SUCCESSOR_TOKEN_HASH);
        assertThat(successor.getExpiresAt()).isEqualTo(nextExpiresAt);
        assertThat(successor.getRotatedAt()).isNull();
    }

    private UserRefreshToken createRefreshToken() {
        return createRefreshToken(NOW.plusSeconds(10), NOW.plusSeconds(100));
    }

    private UserRefreshToken createRefreshToken(
            Instant expiresAt,
            Instant absoluteExpiresAt
    ) {
        return UserRefreshToken.create(
                createUser(),
                UUID.randomUUID(),
                TOKEN_HASH,
                expiresAt,
                absoluteExpiresAt);
    }

    private User createUser() {
        String unique = UUID.randomUUID().toString();
        return User.create(
                null,
                new SignatureStorageKeys(
                        "signatures/original/" + unique,
                        "signatures/thumbnail/" + unique));
    }
}
