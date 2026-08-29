package com.chalkak.backend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.auth.domain.AccessTokenScope;
import com.chalkak.backend.auth.infrastructure.infra.access.JwtAccessTokenProvider;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@AutoConfigureMockMvc
@Import(AdminSecurityFilterChainTest.TestAdminController.class)
@TestPropertySource(properties =
        "chalkak.admin.authentication.development-bypass-enabled=false")
class AdminSecurityFilterChainTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

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
