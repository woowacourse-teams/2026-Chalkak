package com.chalkak.backend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.repository.AdminRepository;
import com.chalkak.backend.auth.domain.AccessTokenScope;
import com.chalkak.backend.auth.infrastructure.infra.access.JwtAccessTokenProvider;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@Transactional
@Import(AdminSecurityFilterChainTest.TestAdminController.class)
@TestPropertySource(properties =
        "chalkak.admin.authentication.development-bypass-enabled=false")
class AdminSecurityFilterChainTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

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
        String token = accessTokenProvider.issue(UUID.randomUUID()).value();

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
    void adminEndpoints_withoutToken_returnsUnauthorized(String method, String path) throws Exception {
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
        String token = accessTokenProvider.issue(UUID.randomUUID()).value();

        // When & Then
        mockMvc.perform(request(HttpMethod.valueOf(method), path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("실제 관리자 로그인 토큰으로 현재 관리자와 감사 로그를 조회하고 로그아웃한다")
    void login_persistedAdmin_accessesProtectedApisAndLogsOut() throws Exception {
        // Given
        Admin admin = adminRepository.save(Admin.create(
                "security-operator", passwordEncoder.encode("test-password")));

        // When
        String response = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"security-operator","password":"test-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminId").value(admin.getId().toString()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("accessToken").asString();

        // Then
        mockMvc.perform(get("/api/v1/admin/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("security-operator"));
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private static Stream<Arguments> protectedAdminEndpoints() {
        String id = "0198f6c1-62ba-7d30-8b12-0f733b6570f6";
        return Stream.of(
                Arguments.of("GET", "/api/v1/admin/auth/me"),
                Arguments.of("POST", "/api/v1/admin/auth/logout"),
                Arguments.of("GET", "/api/v1/admin/posts"),
                Arguments.of("GET", "/api/v1/admin/posts/" + id),
                Arguments.of("PUT", "/api/v1/admin/posts/" + id + "/moderation"),
                Arguments.of("DELETE", "/api/v1/admin/posts/" + id),
                Arguments.of("GET", "/api/v1/admin/users"),
                Arguments.of("GET", "/api/v1/admin/users/" + id),
                Arguments.of("PATCH", "/api/v1/admin/users/" + id + "/status"),
                Arguments.of("GET", "/api/v1/admin/topics"),
                Arguments.of("POST", "/api/v1/admin/topics"),
                Arguments.of("GET", "/api/v1/admin/topics/" + id),
                Arguments.of("PUT", "/api/v1/admin/topics/" + id),
                Arguments.of("DELETE", "/api/v1/admin/topics/" + id),
                Arguments.of("GET", "/api/v1/admin/audit-logs")
        );
    }

    @RestController
    public static class TestAdminController {

        @GetMapping("/api/v1/admin/security-test")
        public AuthenticatedAdmin getCurrentAdmin(
                @com.chalkak.backend.admin.api.support.CurrentAdmin
                AuthenticatedAdmin authenticatedAdmin
        ) {
            return authenticatedAdmin;
        }
    }
}
