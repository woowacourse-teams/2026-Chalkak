package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import com.chalkak.backend.auth.service.TokenRefreshResult;
import com.chalkak.backend.auth.service.UserRefreshTokenService;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.notification.domain.PushDevice;
import com.chalkak.backend.notification.infrastructure.persistence.PushDeviceJpaRepository;
import com.chalkak.backend.notification.repository.PushDeviceRepository;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class PushDeviceRefreshRevocationTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant REVOKED_AT = NOW.plusSeconds(60);

    @Autowired
    private UserRefreshTokenService userRefreshTokenService;

    private final Map<UUID, UUID> registeredDeviceIds = new HashMap<>();

    @Autowired
    private PushDeviceService pushDeviceService;

    @Autowired
    private PushDeviceRepository pushDeviceRepository;

    @Autowired
    private PushDeviceJpaRepository pushDeviceJpaRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

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
        jdbcTemplate.execute("DROP TABLE IF EXISTS push_device_refresh_guard");
        new DatabaseCleaner(jdbcTemplate).clean();
    }

    @ParameterizedTest
    @EnumSource(RevocationCause.class)
    @DisplayName("재사용·비활동 만료·절대 만료로 갱신이 401을 반환해도 해당 RT와 기기 해제는 커밋한다")
    void refreshApi_reusedOrExpiredToken_commitsRevocationAndDeviceCleanup(
            RevocationCause cause
    ) throws Exception {
        // Given
        User user = userRepository.save(UserFixture.create());
        IssuedRefreshToken first = register(user, "first-device");
        IssuedRefreshToken second = register(user, "second-device");
        IssuedRefreshToken other = register(userRepository.save(UserFixture.create()),
                "other-device");
        prepareRevocation(first, cause);

        // When
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + first.value() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("REAUTHENTICATION_REQUIRED"));

        // Then
        assertRevoked(first.sessionId());
        assertDeleted(first);
        assertActive(second, "second-device");
        assertActive(other, "other-device");
        assertThat(countLiveTokens(second.sessionId())).isEqualTo(1);
        assertThat(countLiveTokens(other.sessionId())).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(RevocationCause.class)
    @DisplayName("재사용·만료 처리에서 기기 행 삭제에 실패하면 RT 폐기도 롤백한다")
    void refresh_deviceCleanupFails_rollsBackTokenRevocation(RevocationCause cause) {
        // Given
        IssuedRefreshToken issued = register(userRepository.save(UserFixture.create()),
                "rollback-device");
        prepareRevocation(issued, cause);
        int liveTokens = countLiveTokens(issued.sessionId());
        blockDeviceDeletion(issued.sessionId());

        // When & Then
        assertThatThrownBy(() -> userRefreshTokenService.refresh(issued.value()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(countLiveTokens(issued.sessionId())).isEqualTo(liveTokens);
        assertActive(issued, "rollback-device");
    }

    @Test
    @DisplayName("정상 토큰 갱신은 로그인 ID와 기존 기기 연결을 유지한다")
    void refresh_liveToken_keepsRegisteredDevice() {
        // Given
        User user = userRepository.save(UserFixture.create());
        IssuedRefreshToken issued = register(user, "normal-device");
        given(clock.instant()).willReturn(REVOKED_AT);

        // When
        TokenRefreshResult result = userRefreshTokenService.refresh(issued.value());

        // Then
        assertThat(result.refreshToken().sessionId()).isEqualTo(issued.sessionId());
        assertActive(issued, "normal-device");
        assertThat(countLiveTokens(issued.sessionId())).isEqualTo(2);
        pushDeviceService.register(user.getId(), result.refreshToken().sessionId(),
                "updated-device");
        assertActive(issued, "updated-device");
    }

    @Test
    @DisplayName("기기가 없는 로그인도 재사용 감지 시 RT를 폐기하고 인증 오류를 반환한다")
    void refresh_sessionWithoutDevice_revokesTokens() {
        // Given
        User user = userRepository.save(UserFixture.create());
        IssuedRefreshToken issued = userRefreshTokenService.issue(user);
        prepareRevocation(issued, RevocationCause.REUSED);

        // When & Then
        assertReauthenticationRequired(issued);
        assertRevoked(issued.sessionId());
        assertThat(pushDeviceRepository.findBySessionId(issued.sessionId())).isEmpty();
    }

    @Test
    @DisplayName("FCM 토큰이 다른 로그인으로 이동했으면 이전 로그인 재사용 감지는 새 기기를 해제하지 않는다")
    void refresh_previousSession_keepsTransferredDevice() {
        // Given
        User user = userRepository.save(UserFixture.create());
        IssuedRefreshToken first = register(user, "shared-device");
        IssuedRefreshToken second = register(user, "shared-device");
        prepareRevocation(first, RevocationCause.REUSED);

        // When & Then
        assertReauthenticationRequired(first);
        assertRevoked(first.sessionId());
        assertDeleted(first);
        assertActive(second, "shared-device");
    }

    @Test
    @DisplayName("이미 폐기된 RT로 갱신을 반복해도 기기는 삭제된 상태를 유지한다")
    void refresh_alreadyRevokedToken_keepsDeviceDeleted() {
        // Given
        IssuedRefreshToken issued = register(userRepository.save(UserFixture.create()),
                "repeated-device");
        prepareRevocation(issued, RevocationCause.REUSED);
        assertReauthenticationRequired(issued);
        given(clock.instant()).willReturn(REVOKED_AT.plusSeconds(60));

        // When & Then
        assertReauthenticationRequired(issued);
        assertRevoked(issued.sessionId());
        assertDeleted(issued);
    }

    private IssuedRefreshToken register(User user, String token) {
        IssuedRefreshToken issued = userRefreshTokenService.issue(user);
        pushDeviceService.register(user.getId(), issued.sessionId(), token);
        registeredDeviceIds.put(issued.sessionId(),
                pushDeviceRepository.findBySessionId(issued.sessionId()).orElseThrow().getId());
        return issued;
    }

    private void blockDeviceDeletion(UUID sessionId) {
        jdbcTemplate.execute("""
                CREATE TABLE push_device_refresh_guard (
                    device_id UUID NOT NULL REFERENCES push_devices(id) ON DELETE RESTRICT
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO push_device_refresh_guard (device_id)
                SELECT id FROM push_devices WHERE session_id = ?
                """, sessionId);
    }

    private void prepareRevocation(IssuedRefreshToken issued, RevocationCause cause) {
        switch (cause) {
            case REUSED -> userRefreshTokenService.refresh(issued.value());
            case INACTIVITY_EXPIRED -> jdbcTemplate.update(
                    "UPDATE user_refresh_tokens SET expires_at = ? WHERE session_id = ?",
                    Timestamp.from(REVOKED_AT), issued.sessionId());
            case ABSOLUTE_EXPIRED -> jdbcTemplate.update("""
                    UPDATE user_refresh_tokens SET expires_at = ?, absolute_expires_at = ?
                    WHERE session_id = ?
                    """, Timestamp.from(REVOKED_AT), Timestamp.from(REVOKED_AT),
                    issued.sessionId());
        }
        given(clock.instant()).willReturn(REVOKED_AT);
    }

    private void assertReauthenticationRequired(IssuedRefreshToken issued) {
        assertThatThrownBy(() -> userRefreshTokenService.refresh(issued.value()))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REAUTHENTICATION_REQUIRED));
    }

    private void assertRevoked(UUID sessionId) {
        assertThat(countLiveTokens(sessionId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_refresh_tokens
                WHERE session_id = ? AND revoked_at = ?
                """, Integer.class, sessionId, Timestamp.from(REVOKED_AT))).isPositive();
    }

    private int countLiveTokens(UUID sessionId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_refresh_tokens
                WHERE session_id = ? AND revoked_at IS NULL
                """, Integer.class, sessionId);
    }

    private void assertDeleted(IssuedRefreshToken issued) {
        assertThat(pushDeviceJpaRepository.findById(
                registeredDeviceIds.get(issued.sessionId()))).isEmpty();
        assertThat(pushDeviceRepository.findBySessionId(issued.sessionId())).isEmpty();
    }

    private void assertActive(IssuedRefreshToken issued, String token) {
        PushDevice device = pushDeviceRepository.findBySessionId(issued.sessionId()).orElseThrow();
        assertThat(device.getRegisteredAt()).isEqualTo(NOW);
        assertThat(device.getFcmToken()).isEqualTo(token);
        assertThat(device.getFcmTokenHash()).isNotNull();
    }

    private enum RevocationCause {
        REUSED, INACTIVITY_EXPIRED, ABSOLUTE_EXPIRED
    }
}
