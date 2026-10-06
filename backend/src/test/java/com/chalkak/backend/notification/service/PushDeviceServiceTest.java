package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import com.chalkak.backend.auth.service.UserRefreshTokenService;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.notification.domain.FcmToken;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PushDeviceServiceTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");

    @Autowired
    private PushDeviceService pushDeviceService;

    @Autowired
    private UserRefreshTokenService userRefreshTokenService;

    @Autowired
    private UserRepository userRepository;

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
    @DisplayName("살아 있는 로그인에 기기를 처음 등록하면 토큰과 등록 시각을 저장한다")
    void register_liveSession_storesDevice() {
        // given
        Login login = login();

        // when
        register(login, "fcm-token");

        // then
        DeviceRow row = findDevice(login.sessionId());
        assertThat(row.id()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT user_id FROM push_devices WHERE id = ?", UUID.class, row.id()))
                .isEqualTo(login.userId());
        assertThat(row.token()).isEqualTo("fcm-token");
        assertThat(row.hash()).isEqualTo(new FcmToken("fcm-token").getHash());
        assertThat(row.registeredAt()).isEqualTo(NOW);
        assertThat(row.updatedAt()).isEqualTo(NOW);
        assertThat(row.disabledAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"first-token", "replacement-token"})
    @DisplayName("같은 로그인에서 다시 등록하거나 토큰이 바뀌어도 행과 최초 등록 시각은 유지한다")
    void register_sameSession_keepsRowAndFirstRegistration(String replacement) {
        // given
        Login login = login();
        register(login, "first-token");
        UUID originalId = findDevice(login.sessionId()).id();
        given(clock.instant()).willReturn(NOW.plusSeconds(60));

        // when
        register(login, replacement);

        // then
        DeviceRow row = findDevice(login.sessionId());
        assertThat(row.id()).isEqualTo(originalId);
        assertThat(row.registeredAt()).isEqualTo(NOW);
        assertThat(row.updatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(row.token()).isEqualTo(replacement);
        assertThat(countDevices()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 회원의 서로 다른 기기는 각각 활성 연결을 유지한다")
    void register_distinctDevicesOfSameUser_keepsBothActive() {
        // given
        Login first = login();
        User user = userRepository.findById(first.userId()).orElseThrow();
        Login second = new Login(user.getId(), userRefreshTokenService.issue(user));
        flushAndClear();

        // when
        register(first, "first-device");
        register(second, "second-device");

        // then
        assertThat(findDevice(first.sessionId()).disabledAt()).isNull();
        assertThat(findDevice(second.sessionId()).disabledAt()).isNull();
        assertThat(countDevices()).isEqualTo(2);
    }

    @Test
    @DisplayName("다른 로그인이 같은 FCM 토큰을 등록하면 이전 연결을 끊고 원문을 제거한다")
    void register_sameTokenOnAnotherUser_transfersActiveBinding() {
        // given
        Login first = login();
        Login second = login();
        register(first, "shared-token");
        given(clock.instant()).willReturn(NOW.plusSeconds(60));

        // when
        register(second, "shared-token");

        // then
        DeviceRow old = findDevice(first.sessionId());
        assertThat(old.disabledAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(old.updatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(old.token()).isNull();
        assertThat(old.hash()).isNull();
        assertThat(findDevice(second.sessionId()).token()).isEqualTo("shared-token");
        assertThat(findDevice(second.sessionId()).disabledAt()).isNull();
    }

    @Test
    @DisplayName("비활성화된 연결을 다시 등록하면 기존 행과 최초 등록 시각으로 활성화한다")
    void register_disabledSession_reactivatesOriginalRow() {
        // given
        Login first = login();
        Login second = login();
        register(first, "shared-token");
        UUID originalId = findDevice(first.sessionId()).id();
        register(second, "shared-token");
        given(clock.instant()).willReturn(NOW.plusSeconds(120));

        // when
        register(first, "shared-token");

        // then
        DeviceRow row = findDevice(first.sessionId());
        assertThat(row.id()).isEqualTo(originalId);
        assertThat(row.registeredAt()).isEqualTo(NOW);
        assertThat(row.disabledAt()).isNull();
        assertThat(findDevice(second.sessionId()).token()).isNull();
    }

    @Test
    @DisplayName("리프레시 토큰 회전 후에도 같은 로그인에 기기를 등록할 수 있다")
    void register_rotatedSessionWithLiveSuccessor_acceptsRegistration() {
        // given
        Login login = login();
        userRefreshTokenService.refresh(login.refreshToken().value());
        flushAndClear();

        // when
        register(login, "fcm-token");

        // then
        assertThat(findDevice(login.sessionId()).token()).isEqualTo("fcm-token");
    }

    @ParameterizedTest
    @ValueSource(strings = {"revoked_at", "rotated_at"})
    @DisplayName("폐기되었거나 후속 토큰 없이 회전 기록만 남은 로그인은 등록할 수 없다")
    void register_unusableSession_doesNotChangeExistingBinding(String column) {
        // given
        Login existing = login();
        Login invalid = login();
        register(existing, "shared-token");
        jdbcTemplate.update(
                "UPDATE user_refresh_tokens SET " + column + " = ? WHERE session_id = ?",
                Timestamp.from(NOW), invalid.sessionId());

        // when & then
        assertReauthenticationRequired(invalid.userId(), invalid.sessionId());
        assertThat(findDevice(existing.sessionId()).token()).isEqualTo("shared-token");
        assertThat(findDevice(existing.sessionId()).disabledAt()).isNull();
        assertThat(countDevices()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"expires_at", "absolute_expires_at"})
    @DisplayName("비활동 또는 절대 만료 직전만 등록할 수 있고 만료 시각부터 거절한다")
    void register_expiryBoundary_acceptsOnlyBeforeExpiry(String column) {
        // given
        Login login = login();
        Instant expiry = NOW.plusSeconds(60);
        // 비활동 만료가 절대 만료를 넘지 않는 기존 DB 제약을 유지한다.
        if (column.equals("absolute_expires_at")) {
            jdbcTemplate.update(
                    "UPDATE user_refresh_tokens SET expires_at = ? WHERE session_id = ?",
                    Timestamp.from(expiry), login.sessionId());
        }
        jdbcTemplate.update(
                "UPDATE user_refresh_tokens SET " + column + " = ? WHERE session_id = ?",
                Timestamp.from(expiry), login.sessionId());
        given(clock.instant()).willReturn(expiry.minusNanos(1_000));

        // when
        register(login, "before-expiry");
        given(clock.instant()).willReturn(expiry);

        // then
        assertReauthenticationRequired(login.userId(), login.sessionId());
        given(clock.instant()).willReturn(expiry.plusNanos(1_000));
        assertReauthenticationRequired(login.userId(), login.sessionId());
        assertThat(findDevice(login.sessionId()).token()).isEqualTo("before-expiry");
    }

    @Test
    @DisplayName("다른 회원의 로그인이나 존재하지 않는 로그인으로 등록할 수 없다")
    void register_unknownOrOtherUsersSession_requiresReauthentication() {
        // given
        Login owner = login();
        Login other = login();

        // when & then
        assertReauthenticationRequired(other.userId(), owner.sessionId());
        assertReauthenticationRequired(owner.userId(), UUID.randomUUID());
        assertReauthenticationRequired(UUID.randomUUID(), owner.sessionId());
        assertReauthenticationRequired(owner.userId(), null);
        assertReauthenticationRequired(null, owner.sessionId());
        assertThat(countDevices()).isZero();
    }

    @Test
    @DisplayName("사용 가능한 리프레시 토큰이 없으면 기기를 등록할 수 없다")
    void register_sessionWithoutToken_requiresReauthentication() {
        // given
        Login login = login();
        jdbcTemplate.update("DELETE FROM user_refresh_tokens WHERE session_id = ?",
                login.sessionId());

        // when & then
        assertReauthenticationRequired(login.userId(), login.sessionId());
        assertThat(countDevices()).isZero();
    }

    @Test
    @DisplayName("탈퇴한 회원은 기존 로그인으로 기기를 등록할 수 없다")
    void register_withdrawnUser_rejectsRegistration() {
        // given
        Login login = login();
        userRepository.findById(login.userId()).orElseThrow().withdraw();
        flushAndClear();

        // when & then
        assertThatThrownBy(
                () -> pushDeviceService.register(login.userId(), login.sessionId(), "fcm-token"))
                .isInstanceOf(UnauthorizedException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(countDevices()).isZero();
    }

    private Login login() {
        User user = userRepository.save(UserFixture.create());
        IssuedRefreshToken token = userRefreshTokenService.issue(user);
        flushAndClear();
        return new Login(user.getId(), token);
    }

    private void register(Login login, String token) {
        pushDeviceService.register(login.userId(), login.sessionId(), token);
        flushAndClear();
    }

    private void assertReauthenticationRequired(UUID userId, UUID sessionId) {
        assertThatThrownBy(() -> pushDeviceService.register(userId, sessionId, "shared-token"))
                .isInstanceOf(UnauthorizedException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REAUTHENTICATION_REQUIRED);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private DeviceRow findDevice(UUID sessionId) {
        return jdbcTemplate.queryForObject("""
                SELECT id, fcm_token, fcm_token_hash, registered_at, updated_at, disabled_at
                FROM push_devices WHERE session_id = ?
                """, (rs, rowNumber) -> new DeviceRow(
                rs.getObject("id", UUID.class), rs.getString("fcm_token"),
                rs.getString("fcm_token_hash"),
                rs.getTimestamp("registered_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getTimestamp("disabled_at") == null
                        ? null
                        : rs.getTimestamp("disabled_at").toInstant()),
                sessionId);
    }

    private int countDevices() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM push_devices", Integer.class);
    }

    private record Login(UUID userId, IssuedRefreshToken refreshToken) {
        UUID sessionId() {
            return refreshToken.sessionId();
        }
    }

    private record DeviceRow(UUID id, String token, String hash, Instant registeredAt,
            Instant updatedAt, Instant disabledAt) {
    }
}
