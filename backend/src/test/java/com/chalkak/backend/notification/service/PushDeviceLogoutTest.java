package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import com.chalkak.backend.auth.repository.UserRefreshTokenRepository;
import com.chalkak.backend.auth.service.UserRefreshTokenService;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.notification.domain.PushDevice;
import com.chalkak.backend.notification.repository.PushDeviceRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

@Transactional
@AutoConfigureMockMvc
class PushDeviceLogoutTest extends IntegrationTestSupport {

    private static final Instant REGISTERED_AT = Instant.parse("2026-09-30T00:00:00Z");
    private static final Instant LOGGED_OUT_AT = REGISTERED_AT.plusSeconds(60);

    @Autowired
    private UserRefreshTokenService userRefreshTokenService;

    private final Map<UUID, UUID> registeredDeviceIds = new HashMap<>();

    @Autowired
    private PushDeviceService pushDeviceService;

    @Autowired
    private PushDeviceRepository pushDeviceRepository;

    @Autowired
    private UserRefreshTokenRepository userRefreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(REGISTERED_AT);
        given(clock.getZone()).willReturn(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("로그아웃한 로그인 기기의 토큰만 제거하고 다른 로그인·회원의 기기는 유지한다")
    void logout_registeredSession_disablesOnlyItsDevice() {
        // Given
        User user = userRepository.save(UserFixture.create());
        IssuedRefreshToken first = register(user, "first-device");
        IssuedRefreshToken second = register(user, "second-device");
        IssuedRefreshToken other = register(userRepository.save(UserFixture.create()),
                "other-user-device");
        flushAndClear();
        given(clock.instant()).willReturn(LOGGED_OUT_AT);

        // When
        userRefreshTokenService.logout(first.value());
        flushAndClear();

        // Then
        PushDevice disabled = entityManager.find(PushDevice.class,
                registeredDeviceIds.get(first.sessionId()));
        assertThat(disabled.getUser()).isNull();
        assertThat(disabled.getSessionId()).isNull();
        assertThat(disabled.getFcmToken()).isNull();
        assertThat(disabled.getFcmTokenHash()).isNull();
        assertThat(disabled.getDisabledAt()).isEqualTo(LOGGED_OUT_AT);
        assertThat(disabled.getUpdatedAt()).isEqualTo(LOGGED_OUT_AT);
        assertThat(disabled.getRegisteredAt()).isEqualTo(REGISTERED_AT);
        assertThat(pushDeviceRepository.findBySessionId(second.sessionId()).orElseThrow()
                .getFcmToken())
                .isEqualTo("second-device");
        assertThat(
                pushDeviceRepository.findBySessionId(other.sessionId()).orElseThrow().getFcmToken())
                .isEqualTo("other-user-device");
        assertThat(userRefreshTokenRepository.existsUsableBySessionIdAndUserId(
                first.sessionId(), user.getId(), LOGGED_OUT_AT)).isFalse();
        assertThat(userRefreshTokenRepository.existsUsableBySessionIdAndUserId(
                second.sessionId(), user.getId(), LOGGED_OUT_AT)).isTrue();
        assertThatThrownBy(
                () -> pushDeviceService.register(user.getId(), first.sessionId(), "new-token"))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REAUTHENTICATION_REQUIRED));
    }

    @Test
    @DisplayName("회전된 이전 RT로 로그아웃해도 같은 로그인 기기와 후속 RT를 폐기한다")
    void logout_rotatedToken_disablesDeviceAndSuccessor() {
        // Given
        User user = userRepository.save(UserFixture.create());
        IssuedRefreshToken issued = register(user, "rotated-device");
        flushAndClear();
        userRefreshTokenService.refresh(issued.value());
        flushAndClear();

        // When
        userRefreshTokenService.logout(issued.value());
        flushAndClear();

        // Then
        assertThat(entityManager.find(PushDevice.class, registeredDeviceIds.get(issued.sessionId()))
                .getFcmToken()).isNull();
        assertThat(userRefreshTokenRepository.existsUsableBySessionIdAndUserId(
                issued.sessionId(), user.getId(), REGISTERED_AT)).isFalse();
    }

    @Test
    @DisplayName("이미 폐기된 RT라도 남아 있는 해당 로그인 기기 토큰을 제거한다")
    void logout_revokedToken_clearsRemainingDeviceToken() {
        // Given
        IssuedRefreshToken issued = register(userRepository.save(UserFixture.create()),
                "revoked-device");
        userRefreshTokenRepository.revokeSession(issued.sessionId(), REGISTERED_AT);
        flushAndClear();

        // When
        userRefreshTokenService.logout(issued.value());
        flushAndClear();

        // Then
        assertThat(entityManager.find(PushDevice.class, registeredDeviceIds.get(issued.sessionId()))
                .getFcmToken()).isNull();
    }

    @Test
    @DisplayName("반복 로그아웃은 최초 비활성화 시각을 유지하고 모르는 RT는 다른 기기에 영향을 주지 않는다")
    void logout_repeatedOrUnknownToken_keepsExistingState() {
        // Given
        User user = userRepository.save(UserFixture.create());
        IssuedRefreshToken first = register(user, "first-device");
        IssuedRefreshToken second = register(user, "second-device");
        flushAndClear();
        given(clock.instant()).willReturn(LOGGED_OUT_AT);
        userRefreshTokenService.logout(first.value());
        flushAndClear();
        given(clock.instant()).willReturn(LOGGED_OUT_AT.plusSeconds(60));

        // When
        userRefreshTokenService.logout(first.value());
        userRefreshTokenService.logout("unknown-refresh-token");
        flushAndClear();

        // Then
        PushDevice disabled = entityManager.find(PushDevice.class,
                registeredDeviceIds.get(first.sessionId()));
        assertThat(disabled.getDisabledAt()).isEqualTo(LOGGED_OUT_AT);
        assertThat(disabled.getUpdatedAt()).isEqualTo(LOGGED_OUT_AT);
        assertThat(disabled.getUser()).isNull();
        assertThat(disabled.getSessionId()).isNull();
        assertThat(disabled.getFcmToken()).isNull();
        assertThat(pushDeviceRepository.findBySessionId(second.sessionId()).orElseThrow()
                .getFcmToken())
                .isEqualTo("second-device");
    }

    @Test
    @DisplayName("기기를 등록하지 않은 로그인도 정상적으로 로그아웃한다")
    void logout_withoutRegisteredDevice_revokesRefreshToken() {
        // Given
        User user = userRepository.save(UserFixture.create());
        IssuedRefreshToken issued = userRefreshTokenService.issue(user);
        flushAndClear();

        // When
        userRefreshTokenService.logout(issued.value());
        flushAndClear();

        // Then
        assertThat(pushDeviceRepository.findBySessionId(issued.sessionId())).isEmpty();
        assertThat(userRefreshTokenRepository.existsUsableBySessionIdAndUserId(
                issued.sessionId(), user.getId(), REGISTERED_AT)).isFalse();
    }

    @Test
    @DisplayName("토큰이 새 로그인으로 이동한 뒤 이전 로그아웃을 처리해도 새 연결은 유지한다")
    void logout_previousSession_keepsTransferredTokenOnNewSession() {
        // Given
        User user = userRepository.save(UserFixture.create());
        IssuedRefreshToken first = register(user, "shared-device");
        IssuedRefreshToken second = register(user, "shared-device");
        flushAndClear();

        // When
        userRefreshTokenService.logout(first.value());
        flushAndClear();

        // Then
        assertThat(pushDeviceRepository.findBySessionId(second.sessionId()).orElseThrow()
                .getFcmToken())
                .isEqualTo("shared-device");
        assertThat(userRefreshTokenRepository.existsUsableBySessionIdAndUserId(
                second.sessionId(), user.getId(), REGISTERED_AT)).isTrue();
    }

    @Test
    @DisplayName("만료된 RT로 로그아웃해도 연결 기기 토큰은 제거한다")
    void logout_expiredToken_clearsDeviceToken() {
        // Given
        IssuedRefreshToken issued = register(userRepository.save(UserFixture.create()),
                "expired-device");
        flushAndClear();
        jdbcTemplate.update("UPDATE user_refresh_tokens SET expires_at = ? WHERE session_id = ?",
                java.sql.Timestamp.from(REGISTERED_AT.minusSeconds(1)), issued.sessionId());

        // When
        userRefreshTokenService.logout(issued.value());
        flushAndClear();

        // Then
        assertThat(entityManager.find(PushDevice.class, registeredDeviceIds.get(issued.sessionId()))
                .getFcmToken()).isNull();
    }

    @Test
    @DisplayName("기존 로그아웃 API는 액세스 토큰 없이 RT로 기기를 해제하고 빈 204를 반환한다")
    void logoutApi_withoutAccessToken_disablesDeviceAndReturnsNoContent() throws Exception {
        // Given
        IssuedRefreshToken issued = register(userRepository.save(UserFixture.create()),
                "api-device");
        flushAndClear();

        // When
        mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + issued.value() + "\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        flushAndClear();

        // Then
        assertThat(entityManager.find(PushDevice.class, registeredDeviceIds.get(issued.sessionId()))
                .getFcmToken()).isNull();
    }

    private IssuedRefreshToken register(User user, String token) {
        IssuedRefreshToken issued = userRefreshTokenService.issue(user);
        pushDeviceService.register(user.getId(), issued.sessionId(), token);
        registeredDeviceIds.put(issued.sessionId(),
                pushDeviceRepository.findBySessionId(issued.sessionId()).orElseThrow().getId());
        return issued;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
