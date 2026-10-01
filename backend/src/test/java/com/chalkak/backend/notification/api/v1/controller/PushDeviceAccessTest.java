package com.chalkak.backend.notification.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.domain.AccessTokenScope;
import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import com.chalkak.backend.auth.infrastructure.infra.access.JwtAccessTokenProvider;
import com.chalkak.backend.auth.service.UserRefreshTokenService;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@AutoConfigureMockMvc
class PushDeviceAccessTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRefreshTokenService userRefreshTokenService;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("유효한 로그인 JWT로 등록하고 갱신하면 같은 DB 행에 새 토큰을 저장한다")
    void registerCurrentDevice_validLogin_registersAndUpdatesDevice() throws Exception {
        // Given
        User user = userRepository.save(UserFixture.create());
        IssuedRefreshToken refreshToken = userRefreshTokenService.issue(user);
        String authorization = "Bearer " + accessTokenProvider
                .issueForSession(user.getId(), refreshToken.sessionId()).value();

        // When & Then
        register(authorization, "first-token");
        UUID deviceId = jdbcTemplate.queryForObject(
                "SELECT id FROM push_devices WHERE session_id = ?", UUID.class,
                refreshToken.sessionId());
        register(authorization, "updated-token");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT id FROM push_devices WHERE session_id = ?", UUID.class,
                refreshToken.sessionId())).isEqualTo(deviceId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT fcm_token FROM push_devices WHERE session_id = ?", String.class,
                refreshToken.sessionId())).isEqualTo("updated-token");
    }

    @Test
    @DisplayName("인증 없는 기기 등록 요청은 필터에서 401로 차단한다")
    void registerCurrentDevice_unauthenticated_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(put("/api/v1/push-devices/current")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fcmToken":"fcm-token"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("관리자 JWT로는 회원 기기를 등록할 수 없다")
    void registerCurrentDevice_adminToken_returnsForbidden() throws Exception {
        // Given
        String authorization = "Bearer " + accessTokenProvider
                .issue(UUID.randomUUID(), AccessTokenScope.ADMIN).value();

        // When & Then
        mockMvc.perform(put("/api/v1/push-devices/current")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fcmToken":"fcm-token"}
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("로그인 ID 없는 이전 JWT는 알림함을 조회하지만 기기 등록은 재로그인이 필요하다")
    void registerCurrentDevice_legacyToken_preservesInboxAndRequiresReauthentication()
            throws Exception {
        // Given
        User user = userRepository.save(UserFixture.create());
        String authorization = "Bearer " + accessTokenProvider
                .issue(user.getId(), AccessTokenScope.USER).value();

        // When & Then
        mockMvc.perform(get("/api/v1/notifications")
                .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/push-devices/current")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fcmToken":"fcm-token"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("REAUTHENTICATION_REQUIRED"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM push_devices", Long.class))
                .isZero();
    }

    private void register(String authorization, String token) throws Exception {
        mockMvc.perform(put("/api/v1/push-devices/current")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fcmToken\":\"" + token + "\"}"))
                .andExpect(status().isNoContent());
    }
}
