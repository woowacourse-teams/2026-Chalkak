package com.chalkak.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.repository.auth.AdminRepository;
import com.chalkak.backend.auth.domain.AccessTokenScope;
import com.chalkak.backend.auth.infrastructure.infra.access.JwtAccessTokenProvider;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.notification.domain.TopicPushSchedule;
import java.time.LocalTime;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@Transactional
@Import(AdminSecurityFilterChainTest.TestAdminController.class)
@TestPropertySource(properties = "chalkak.admin.authentication.development-bypass-enabled=false")
class AdminSecurityFilterChainTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("관리자 로그인은 액세스 토큰 없이 호출할 수 있다")
    void login_withoutToken_reachesController() throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "operator",
                          "password": "wrong-password"
                        }
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("아이디 또는 비밀번호가 올바르지 않습니다."));
    }

    @Test
    @DisplayName("토큰 없이 관리자 API를 호출하면 401을 반환한다")
    void adminApi_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/security-test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("일반 사용자 액세스 토큰으로 관리자 API를 호출하면 403을 반환한다")
    void adminApi_userToken_returnsForbidden() throws Exception {
        String token = accessTokenProvider.issueForSession(UUID.randomUUID(), UUID.randomUUID())
                .value();

        mockMvc.perform(get("/api/v1/admin/security-test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("관리자 액세스 토큰으로 관리자 API를 호출하면 현재 관리자를 주입한다")
    void adminApi_adminToken_injectsCurrentAdmin() throws Exception {
        UUID adminId = UUID.randomUUID();
        String token = accessTokenProvider.issue(adminId, AccessTokenScope.ADMIN).value();

        mockMvc.perform(get("/api/v1/admin/security-test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminId").value(adminId.toString()));
    }

    @ParameterizedTest
    @MethodSource("protectedAdminEndpoints")
    @DisplayName("로그인 외 모든 실제 관리자 API는 무인증 요청을 401로 거부한다")
    void adminEndpoints_withoutToken_returnsUnauthorized(String method, String path)
            throws Exception {
        // When & Then
        mockMvc.perform(request(HttpMethod.valueOf(method), path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @ParameterizedTest
    @MethodSource("protectedAdminEndpoints")
    @DisplayName("로그인 외 모든 실제 관리자 API는 회원 토큰을 403으로 거부한다")
    void adminEndpoints_userToken_returnsForbidden(String method, String path) throws Exception {
        // Given
        String token = accessTokenProvider.issueForSession(UUID.randomUUID(), UUID.randomUUID())
                .value();

        // When & Then
        mockMvc.perform(request(HttpMethod.valueOf(method), path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("실제 관리자 로그인 토큰으로 현재 관리자와 감사 로그를 조회한다")
    void login_persistedAdmin_accessesProtectedApis() throws Exception {
        // Given
        Admin admin = adminRepository.save(Admin.create(
                "security-operator", passwordEncoder.encode("test-password")));

        // When
        String token = login(admin.getId(), "security-operator").accessToken();

        // Then
        mockMvc.perform(get("/api/v1/admin/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("security-operator"));
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("관리자 재발급과 로그아웃은 액세스 토큰 없이 리프레시 토큰만으로 호출한다")
    void refreshAndLogout_withoutAccessToken_reachController() throws Exception {
        // Given
        Admin admin = adminRepository.save(Admin.create(
                "refresh-security-operator", passwordEncoder.encode("test-password")));
        String refreshToken = login(admin.getId(), "refresh-security-operator")
                .refreshToken();

        // When
        String refreshed = mockMvc.perform(post("/api/v1/admin/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String rotatedRefreshToken = objectMapper.readTree(refreshed)
                .get("refreshToken").asString();

        // Then
        mockMvc.perform(post("/api/v1/admin/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + rotatedRefreshToken + "\"}"))
                .andExpect(status().isNoContent());
        // 폐기는 벌크 UPDATE라 영속성 컨텍스트의 엔티티가 낡은 채로 남는다. 테스트가 요청들과
        // 트랜잭션을 공유하므로, 비우지 않으면 다음 요청이 DB가 아니라 낡은 엔티티를 보게 된다.
        entityManager.clear();
        mockMvc.perform(post("/api/v1/admin/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + rotatedRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("REAUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("알 수 없는 리프레시 토큰으로 재발급해도 관리자 인증이 아니라 재로그인 필요로 거절한다")
    void refresh_unknownRefreshToken_returnsReauthenticationRequired() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/admin/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken":"unknown-admin-refresh-token"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("REAUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("관리자 토큰으로 주제 푸시 설정 목록을 조회할 수 있다")
    void getTopicPushSchedules_adminToken_returnsOk() throws Exception {
        // Given
        String token = accessTokenProvider.issue(UUID.randomUUID(), AccessTokenScope.ADMIN).value();

        // When & Then
        mockMvc.perform(get("/api/v1/admin/topic-push-schedules")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedules").isArray());
    }

    @Test
    @DisplayName("관리자 토큰으로 실제 주제 푸시 설정을 생성할 수 있다")
    void createTopicPushSchedule_adminToken_returnsCreated() throws Exception {
        // Given
        String token = accessTokenProvider.issue(UUID.randomUUID(), AccessTokenScope.ADMIN).value();

        // When & Then
        mockMvc.perform(post("/api/v1/admin/topic-push-schedules")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sendTime\":\"14:35:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sendTime").value("14:35:00"))
                .andExpect(jsonPath("$.enabled").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"09:00:01", "09:00:00.000000001"})
    @DisplayName("초 또는 소수 초가 있는 생성 요청은 400으로 거절한다")
    void createTopicPushSchedule_nonMinuteSendTime_returnsBadRequest(String time) throws Exception {
        // Given
        String token = accessTokenProvider.issue(UUID.randomUUID(), AccessTokenScope.ADMIN).value();

        // When & Then
        mockMvc.perform(post("/api/v1/admin/topic-push-schedules")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sendTime\":\"" + time + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("주제 푸시 발송 시각은 분 단위의 KST 시각이어야 합니다."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "BANNED", "WITHDRAWN"})
    @DisplayName("정상·정지·탈퇴 회원 모두 관리자 주제 푸시 설정을 생성할 수 없다")
    void createTopicPushSchedule_memberState_returnsForbidden(String state) throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (id, email, status, signature_original_storage_key,
                    created_at, updated_at, deleted_at)
                VALUES (?, ?, CAST(? AS user_status), ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    CASE WHEN ? = 'WITHDRAWN' THEN CURRENT_TIMESTAMP ELSE NULL END)
                """, userId, userId + "@schedule.test",
                "WITHDRAWN".equals(state) ? "ACTIVE" : state, userId + "/signature.png", state);
        String token = accessTokenProvider.issueForSession(userId, UUID.randomUUID()).value();

        // When & Then
        mockMvc.perform(post("/api/v1/admin/topic-push-schedules")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sendTime\":\"09:00:00\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "BANNED", "WITHDRAWN"})
    @DisplayName("정상·정지·탈퇴 회원 모두 관리자 주제 푸시 설정을 조회할 수 없다")
    void getTopicPushSchedules_memberState_returnsForbidden(String state) throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (id, email, status, signature_original_storage_key,
                    created_at, updated_at, deleted_at)
                VALUES (?, ?, CAST(? AS user_status), ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    CASE WHEN ? = 'WITHDRAWN' THEN CURRENT_TIMESTAMP ELSE NULL END)
                """, userId, userId + "@schedule.test",
                "WITHDRAWN".equals(state) ? "ACTIVE" : state, userId + "/signature.png", state);
        String token = accessTokenProvider.issueForSession(userId, UUID.randomUUID()).value();

        // When & Then
        mockMvc.perform(get("/api/v1/admin/topic-push-schedules")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    private IssuedTokens login(UUID adminId, String username) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"test-password"}
                        """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminId").value(adminId.toString()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        return new IssuedTokens(
                body.get("accessToken").asString(),
                body.get("refreshToken").asString());
    }

    private record IssuedTokens(
            String accessToken,
            String refreshToken) {
    }

    private static Stream<Arguments> protectedAdminEndpoints() {
        String id = "0198f6c1-62ba-7d30-8b12-0f733b6570f6";
        return Stream.of(
                Arguments.of("GET", "/api/v1/admin/auth/me"),
                Arguments.of("GET", "/api/v1/admin/posts"),
                Arguments.of("GET", "/api/v1/admin/posts/" + id),
                Arguments.of("PUT", "/api/v1/admin/posts/" + id + "/moderation"),
                Arguments.of("DELETE", "/api/v1/admin/posts/" + id),
                Arguments.of("GET", "/api/v1/admin/users"),
                Arguments.of("GET", "/api/v1/admin/users/" + id),
                Arguments.of("PATCH", "/api/v1/admin/users/" + id + "/status"),
                Arguments.of("GET", "/api/v1/admin/topics"),
                Arguments.of("POST", "/api/v1/admin/topics"),
                Arguments.of("POST", "/api/v1/admin/topic-push-schedules"),
                Arguments.of("GET", "/api/v1/admin/topic-push-schedules"),
                Arguments.of("PUT", "/api/v1/admin/topic-push-schedules/" + id),
                Arguments.of("DELETE", "/api/v1/admin/topic-push-schedules/" + id),
                Arguments.of("GET", "/api/v1/admin/topics/" + id),
                Arguments.of("PUT", "/api/v1/admin/topics/" + id),
                Arguments.of("DELETE", "/api/v1/admin/topics/" + id),
                Arguments.of("GET", "/api/v1/admin/audit-logs"));
    }

    @RestController
    public static class TestAdminController {

        @GetMapping("/api/v1/admin/security-test")
        public AuthenticatedAdmin getCurrentAdmin(
                @com.chalkak.backend.admin.api.support.CurrentAdmin AuthenticatedAdmin authenticatedAdmin
        ) {
            return authenticatedAdmin;
        }
    }

    @Test
    @DisplayName("관리자 토큰으로 실제 설정 시각을 수정할 수 있다")
    void updateTopicPushSchedule_adminToken_returnsUpdatedSchedule() throws Exception {
        // Given
        TopicPushSchedule schedule = new TopicPushSchedule(LocalTime.of(9, 0));
        entityManager.persist(schedule);
        entityManager.flush();
        String token = accessTokenProvider.issue(UUID.randomUUID(), AccessTokenScope.ADMIN).value();

        // When & Then
        mockMvc.perform(put("/api/v1/admin/topic-push-schedules/" + schedule.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sendTime\":\"18:30:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value(schedule.getId().toString()))
                .andExpect(jsonPath("$.sendTime").value("18:30:00"));
        entityManager.clear();
        assertThat(
                entityManager.find(TopicPushSchedule.class, schedule.getId()).getSendTime())
                .isEqualTo(LocalTime.of(18, 30));
    }

    @ParameterizedTest
    @ValueSource(strings = {"09:00:01", "09:00:00.000000001"})
    @DisplayName("초 또는 소수 초가 있는 수정 요청은 400으로 거절하고 원래 시각을 유지한다")
    void updateTopicPushSchedule_nonMinuteSendTime_returnsBadRequest(String time) throws Exception {
        // Given
        TopicPushSchedule schedule = new TopicPushSchedule(LocalTime.of(9, 0));
        entityManager.persist(schedule);
        entityManager.flush();
        String token = accessTokenProvider.issue(UUID.randomUUID(), AccessTokenScope.ADMIN).value();

        // When & Then
        mockMvc.perform(put("/api/v1/admin/topic-push-schedules/" + schedule.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sendTime\":\"" + time + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
        assertThat(schedule.getSendTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "BANNED", "WITHDRAWN"})
    @DisplayName("정상·정지·탈퇴 회원 모두 관리자 주제 푸시 설정을 수정할 수 없다")
    void updateTopicPushSchedule_memberState_returnsForbidden(String state) throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (id, email, status, signature_original_storage_key,
                    created_at, updated_at, deleted_at)
                VALUES (?, ?, CAST(? AS user_status), ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    CASE WHEN ? = 'WITHDRAWN' THEN CURRENT_TIMESTAMP ELSE NULL END)
                """, userId, userId + "@schedule.test",
                "WITHDRAWN".equals(state) ? "ACTIVE" : state, userId + "/signature.png", state);
        String token = accessTokenProvider.issueForSession(userId, UUID.randomUUID()).value();

        // When & Then
        mockMvc.perform(put("/api/v1/admin/topic-push-schedules/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sendTime\":\"09:00:00\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("관리자 토큰으로 실제 주제 푸시 설정을 삭제할 수 있다")
    void deleteTopicPushSchedule_adminToken_deletesSchedule() throws Exception {
        // Given
        TopicPushSchedule schedule = new TopicPushSchedule(LocalTime.of(9, 0));
        entityManager.persist(schedule);
        entityManager.flush();
        UUID scheduleId = schedule.getId();
        String token = accessTokenProvider.issue(UUID.randomUUID(), AccessTokenScope.ADMIN).value();

        // When & Then
        mockMvc.perform(delete("/api/v1/admin/topic-push-schedules/" + scheduleId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();
        assertThat(entityManager.find(TopicPushSchedule.class, scheduleId)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "BANNED", "WITHDRAWN"})
    @DisplayName("정상·정지·탈퇴 회원 모두 관리자 주제 푸시 설정을 삭제할 수 없다")
    void deleteTopicPushSchedule_memberState_returnsForbidden(String state) throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (id, email, status, signature_original_storage_key,
                    created_at, updated_at, deleted_at)
                VALUES (?, ?, CAST(? AS user_status), ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    CASE WHEN ? = 'WITHDRAWN' THEN CURRENT_TIMESTAMP ELSE NULL END)
                """, userId, userId + "@schedule.test",
                "WITHDRAWN".equals(state) ? "ACTIVE" : state, userId + "/signature.png", state);
        String token = accessTokenProvider.issueForSession(userId, UUID.randomUUID()).value();

        // When & Then
        mockMvc.perform(delete("/api/v1/admin/topic-push-schedules/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }
}
