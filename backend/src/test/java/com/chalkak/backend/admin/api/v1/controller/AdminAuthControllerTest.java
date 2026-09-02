package com.chalkak.backend.admin.api.v1.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AdminArgumentResolverWebMvcConfig;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.service.AdminAuthenticationService;
import com.chalkak.backend.admin.service.AdminLoginResult;
import com.chalkak.backend.admin.service.CurrentAdminResult;
import com.chalkak.backend.auth.domain.IssuedAccessToken;
import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, AdminArgumentResolverWebMvcConfig.class})
class AdminAuthControllerTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final String USERNAME = "operator";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAuthenticationService service;

    @MockitoBean
    private AdminActorResolver adminActorResolver;

    @BeforeEach
    void setUp() {
        given(adminActorResolver.resolve()).willReturn(new AuthenticatedAdmin(ADMIN_ID));
    }

    @Test
    @DisplayName("관리자 로그인에 성공하면 액세스 토큰과 리프레시 토큰을 함께 반환한다")
    void login_validRequest_returnsAccessTokenAndRefreshToken() throws Exception {
        // Given
        given(service.login(USERNAME, "safe-password"))
                .willReturn(new AdminLoginResult(
                        ADMIN_ID,
                        USERNAME,
                        new IssuedAccessToken("admin-token", Duration.ofMinutes(15)),
                        new IssuedRefreshToken("admin-refresh-token", Duration.ofDays(30))
                ));

        // When & Then
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "operator",
                                  "password": "safe-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminId").value(ADMIN_ID.toString()))
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.accessToken").value("admin-token"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").value("admin-refresh-token"))
                .andExpect(jsonPath("$.refreshTokenExpiresIn").value(2592000));
    }

    @Test
    @DisplayName("관리자 로그인 아이디가 비어 있으면 400을 반환한다")
    void login_blankUsername_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": " ",
                                  "password": "safe-password"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("인증된 관리자는 자신의 식별자와 아이디를 조회한다")
    void getCurrentAdmin_authenticatedAdmin_returnsCurrentAdmin() throws Exception {
        // Given
        given(service.getCurrentAdmin(ADMIN_ID))
                .willReturn(new CurrentAdminResult(ADMIN_ID, USERNAME));

        // When & Then
        mockMvc.perform(get("/api/v1/admin/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminId").value(ADMIN_ID.toString()))
                .andExpect(jsonPath("$.username").value(USERNAME));
    }

    @Test
    @DisplayName("인증된 관리자는 로그아웃 응답을 받고 브라우저가 토큰을 폐기할 수 있다")
    void logout_authenticatedAdmin_returnsNoContent() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/admin/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(status().isNoContent());

        then(adminActorResolver).should().resolve();
    }
}
