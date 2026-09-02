package com.chalkak.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
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
class UserRefreshTokenServiceTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");
    private static final String TOKEN_HASH_PATTERN = "^[0-9a-f]{64}$";

    @Autowired
    private UserRefreshTokenService userRefreshTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenHasher refreshTokenHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    @DisplayName("리프레시 토큰을 발급하면 평문은 반환만 하고 저장소에는 해시만 남는다")
    void issue_newUser_storesHashOnlyAndReturnsPlainToken() {
        // given
        User user = saveUser();

        // when
        IssuedRefreshToken issued = userRefreshTokenService.issue(user);

        // then
        flushAndClear();
        String storedTokenHash = jdbcTemplate.queryForObject("""
                SELECT token_hash FROM user_refresh_tokens WHERE user_id = ?
                """, String.class, user.getId());
        assertThat(issued.value()).isNotBlank();
        assertThat(storedTokenHash).isNotEqualTo(issued.value());
        assertThat(storedTokenHash).matches(TOKEN_HASH_PATTERN);
        assertThat(issued.expiresIn()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    @DisplayName("살아 있는 토큰으로 재발급하면 새 토큰을 내주고 기존 토큰은 회전 기록만 남긴다")
    void refresh_liveToken_rotatesConsumedTokenAndReissuesAccessToken() {
        // given
        User user = saveUser();
        IssuedRefreshToken issued = userRefreshTokenService.issue(user);
        flushAndClear();

        // when
        TokenRefreshResult result = userRefreshTokenService.refresh(issued.value());

        // then
        flushAndClear();
        TokenRow consumed = findTokenRow(issued.value());
        TokenRow successor = findTokenRow(result.refreshToken().value());
        assertThat(result.refreshToken().value()).isNotEqualTo(issued.value());
        assertThat(result.accessToken().value()).isNotBlank();
        assertThat(consumed.rotatedAt()).isEqualTo(NOW);
        assertThat(consumed.revokedAt()).isNull();
        assertThat(successor.rotatedAt()).isNull();
        assertThat(successor.sessionId()).isEqualTo(consumed.sessionId());
        assertThat(successor.absoluteExpiresAt()).isEqualTo(consumed.absoluteExpiresAt());
    }

    @Test
    @DisplayName("유예 시간 안에 같은 토큰으로 다시 재발급하면 계보를 끊지 않고 새 토큰을 내준다")
    void refresh_rotatedTokenWithinReuseGrace_issuesAnotherSuccessor() {
        // given
        User user = saveUser();
        IssuedRefreshToken issued = userRefreshTokenService.issue(user);
        flushAndClear();
        IssuedRefreshToken firstSuccessor = refreshAt(issued.value(), NOW);

        // when
        IssuedRefreshToken secondSuccessor = refreshAt(
                issued.value(),
                NOW.plusSeconds(9));

        // then
        UUID sessionId = findTokenRow(issued.value()).sessionId();
        assertThat(secondSuccessor.value()).isNotEqualTo(firstSuccessor.value());
        assertThat(countLiveTokens(sessionId)).isEqualTo(3);
    }

    @Test
    @DisplayName("유예 시간이 지난 뒤 회전된 토큰을 다시 내면 계보 전체를 폐기하고 재로그인을 요구한다")
    void refresh_rotatedTokenAfterReuseGrace_revokesWholeLineage() {
        // given
        User user = saveUser();
        IssuedRefreshToken issued = userRefreshTokenService.issue(user);
        flushAndClear();
        refreshAt(issued.value(), NOW);
        UUID sessionId = findTokenRow(issued.value()).sessionId();
        Instant reusedAt = NOW.plusSeconds(11);
        given(clock.instant()).willReturn(reusedAt);

        // when
        // then
        assertReauthenticationRequired(issued.value());
        flushAndClear();
        assertThat(countLiveTokens(sessionId)).isZero();
        assertThat(findTokenRow(issued.value()).revokedAt()).isEqualTo(reusedAt);
    }

    @Test
    @DisplayName("한 계보를 폐기해도 다른 기기의 계보는 그대로 재발급할 수 있다")
    void refresh_reusedLineageRevoked_keepsOtherDeviceLineageUsable() {
        // given
        User user = saveUser();
        IssuedRefreshToken firstDevice = userRefreshTokenService.issue(user);
        IssuedRefreshToken secondDevice = userRefreshTokenService.issue(user);
        flushAndClear();
        refreshAt(firstDevice.value(), NOW);
        given(clock.instant()).willReturn(NOW.plusSeconds(11));
        assertReauthenticationRequired(firstDevice.value());
        flushAndClear();

        // when
        TokenRefreshResult result = userRefreshTokenService.refresh(secondDevice.value());

        // then
        flushAndClear();
        assertThat(result.refreshToken().value()).isNotBlank();
        assertThat(findTokenRow(secondDevice.value()).revokedAt()).isNull();
    }

    @Test
    @DisplayName("비활동 만료가 지난 토큰은 절대 만료가 남아 있어도 재로그인을 요구한다")
    void refresh_inactivityExpiredToken_revokesLineageAndRequiresReauthentication() {
        // given
        User user = saveUser();
        IssuedRefreshToken issued = userRefreshTokenService.issue(user);
        flushAndClear();
        TokenRow stored = findTokenRow(issued.value());
        Instant inactivityExpiredAt = NOW.plus(Duration.ofDays(30));
        given(clock.instant()).willReturn(inactivityExpiredAt);

        // when
        // then
        assertThat(stored.absoluteExpiresAt()).isAfter(inactivityExpiredAt);
        assertReauthenticationRequired(issued.value());
        flushAndClear();
        assertThat(countLiveTokens(stored.sessionId())).isZero();
    }

    @Test
    @DisplayName("회전을 이어가도 절대 만료 시각에 도달하면 재로그인을 요구한다")
    void refresh_absoluteExpiredToken_revokesLineageAndRequiresReauthentication() {
        // given
        User user = saveUser();
        IssuedRefreshToken issued = userRefreshTokenService.issue(user);
        flushAndClear();
        IssuedRefreshToken lastToken = rotateUntilAbsoluteExpiry(issued.value());
        TokenRow stored = findTokenRow(lastToken.value());
        given(clock.instant()).willReturn(stored.absoluteExpiresAt());

        // when
        // then
        assertReauthenticationRequired(lastToken.value());
        flushAndClear();
        assertThat(countLiveTokens(stored.sessionId())).isZero();
    }

    @Test
    @DisplayName("절대 만료 직전에 회전해도 새 토큰의 비활동 만료는 절대 만료를 넘지 않는다")
    void refresh_rotationNearAbsoluteExpiry_clipsExpiresAtToAbsoluteExpiry() {
        // given
        User user = saveUser();
        IssuedRefreshToken issued = userRefreshTokenService.issue(user);
        flushAndClear();

        // when
        IssuedRefreshToken lastToken = rotateUntilAbsoluteExpiry(issued.value());

        // then
        TokenRow stored = findTokenRow(lastToken.value());
        assertThat(stored.expiresAt()).isEqualTo(stored.absoluteExpiresAt());
        assertThat(stored.absoluteExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(90)));
    }

    @Test
    @DisplayName("존재하지 않는 토큰으로 재발급하면 재로그인을 요구한다")
    void refresh_unknownToken_requiresReauthentication() {
        // given
        String unknownToken = "unknown-refresh-token";

        // when
        // then
        assertReauthenticationRequired(unknownToken);
    }

    @Test
    @DisplayName("이미 폐기된 토큰으로 재발급하면 폐기 시각을 덮어쓰지 않고 재로그인을 요구한다")
    void refresh_revokedToken_keepsRevokedAtAndRequiresReauthentication() {
        // given
        User user = saveUser();
        IssuedRefreshToken issued = userRefreshTokenService.issue(user);
        flushAndClear();
        refreshAt(issued.value(), NOW);
        given(clock.instant()).willReturn(NOW.plusSeconds(11));
        assertReauthenticationRequired(issued.value());
        flushAndClear();
        given(clock.instant()).willReturn(NOW.plusSeconds(30));

        // when
        // then
        assertReauthenticationRequired(issued.value());
        flushAndClear();
        assertThat(findTokenRow(issued.value()).revokedAt()).isEqualTo(NOW.plusSeconds(11));
    }

    @Test
    @DisplayName("차단된 회원도 회원 상태를 읽지 않고 재발급할 수 있다")
    void refresh_bannedUser_stillRotatesToken() {
        // given
        User user = saveUser();
        IssuedRefreshToken issued = userRefreshTokenService.issue(user);
        userRepository.findById(user.getId()).orElseThrow().ban();
        flushAndClear();

        // when
        TokenRefreshResult result = userRefreshTokenService.refresh(issued.value());

        // then
        assertThat(result.refreshToken().value()).isNotEqualTo(issued.value());
        assertThat(result.accessToken().value()).isNotBlank();
    }

    @Test
    @DisplayName("로그아웃하면 그 기기의 계보만 폐기하고 재발급을 막는다")
    void logout_liveToken_revokesOnlyThatLineage() {
        // given
        User user = saveUser();
        IssuedRefreshToken loggedOutDevice = userRefreshTokenService.issue(user);
        IssuedRefreshToken otherDevice = userRefreshTokenService.issue(user);
        flushAndClear();

        // when
        userRefreshTokenService.logout(loggedOutDevice.value());

        // then
        flushAndClear();
        assertReauthenticationRequired(loggedOutDevice.value());
        flushAndClear();
        assertThat(countLiveTokens(findTokenRow(loggedOutDevice.value()).sessionId())).isZero();
        assertThat(findTokenRow(otherDevice.value()).revokedAt()).isNull();
    }

    @Test
    @DisplayName("모르는 토큰이나 이미 폐기된 토큰으로 로그아웃해도 예외를 던지지 않는다")
    void logout_unknownOrRevokedToken_doesNothing() {
        // given
        User user = saveUser();
        IssuedRefreshToken issued = userRefreshTokenService.issue(user);
        flushAndClear();
        userRefreshTokenService.logout(issued.value());
        flushAndClear();

        // when
        // then
        assertThatCode(() -> userRefreshTokenService.logout(issued.value()))
                .doesNotThrowAnyException();
        assertThatCode(() -> userRefreshTokenService.logout("unknown-refresh-token"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("회원의 모든 기기 계보를 한 번에 폐기한다")
    void revokeAll_multipleDevices_revokesEveryLineage() {
        // given
        User user = saveUser();
        IssuedRefreshToken firstDevice = userRefreshTokenService.issue(user);
        IssuedRefreshToken secondDevice = userRefreshTokenService.issue(user);
        flushAndClear();

        // when
        userRefreshTokenService.revokeAll(user.getId());

        // then
        flushAndClear();
        assertThat(findTokenRow(firstDevice.value()).revokedAt()).isEqualTo(NOW);
        assertThat(findTokenRow(secondDevice.value()).revokedAt()).isEqualTo(NOW);
    }

    private User saveUser() {
        User user = userRepository.save(UserFixture.create());
        flushAndClear();
        return user;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private IssuedRefreshToken refreshAt(String presentedToken, Instant at) {
        given(clock.instant()).willReturn(at);
        TokenRefreshResult result = userRefreshTokenService.refresh(presentedToken);
        flushAndClear();
        return result.refreshToken();
    }

    /** 절대 만료 직전까지 비활동 만료를 갱신하며 회전시켜, 마지막 토큰을 돌려준다. */
    private IssuedRefreshToken rotateUntilAbsoluteExpiry(String firstToken) {
        IssuedRefreshToken token = refreshAt(firstToken, NOW.plus(Duration.ofDays(29)));
        token = refreshAt(token.value(), NOW.plus(Duration.ofDays(58)));
        return refreshAt(token.value(), NOW.plus(Duration.ofDays(87)));
    }

    private void assertReauthenticationRequired(String presentedToken) {
        assertThatThrownBy(() -> userRefreshTokenService.refresh(presentedToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("다시 로그인해 주세요.")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REAUTHENTICATION_REQUIRED);
    }

    private int countLiveTokens(UUID sessionId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_refresh_tokens
                WHERE session_id = ? AND revoked_at IS NULL
                """, Integer.class, sessionId);
    }

    private TokenRow findTokenRow(String token) {
        return jdbcTemplate.queryForObject("""
                        SELECT session_id, rotated_at, revoked_at, expires_at, absolute_expires_at
                        FROM user_refresh_tokens
                        WHERE token_hash = ?
                        """,
                (resultSet, rowNumber) -> new TokenRow(
                        resultSet.getObject("session_id", UUID.class),
                        toInstant(resultSet.getTimestamp("rotated_at")),
                        toInstant(resultSet.getTimestamp("revoked_at")),
                        toInstant(resultSet.getTimestamp("expires_at")),
                        toInstant(resultSet.getTimestamp("absolute_expires_at"))),
                refreshTokenHasher.encode(token));
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant();
    }

    private record TokenRow(
            UUID sessionId,
            Instant rotatedAt,
            Instant revokedAt,
            Instant expiresAt,
            Instant absoluteExpiresAt
    ) {
    }
}
