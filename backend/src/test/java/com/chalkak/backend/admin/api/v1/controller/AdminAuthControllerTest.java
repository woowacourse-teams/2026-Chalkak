package com.chalkak.backend.admin.api.v1.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
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
import com.chalkak.backend.admin.service.AdminRefreshTokenService;
import com.chalkak.backend.admin.service.CurrentAdminResult;
import com.chalkak.backend.auth.domain.IssuedAccessToken;
import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import com.chalkak.backend.auth.service.TokenRefreshResult;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.UnauthorizedException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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
    private AdminRefreshTokenService adminRefreshTokenService;

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
    @DisplayName("리프레시 토큰으로 재발급하면 새 액세스 토큰과 회전된 리프레시 토큰을 반환한다")
    void refresh_validRefreshToken_returnsRotatedTokens() throws Exception {
        // Given
        given(adminRefreshTokenService.refresh("admin-refresh-token"))
                .willReturn(new TokenRefreshResult(
                        new IssuedAccessToken("rotated-admin-token", Duration.ofMinutes(15)),
                        new IssuedRefreshToken(
                                "rotated-admin-refresh-token",
                                Duration.ofDays(30))
                ));

        // When & Then
        mockMvc.perform(post("/api/v1/admin/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "admin-refresh-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("rotated-admin-token"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").value("rotated-admin-refresh-token"))
                .andExpect(jsonPath("$.refreshTokenExpiresIn").value(2592000));
    }

    @Test
    @DisplayName("재발급 요청의 리프레시 토큰이 비어 있으면 400을 반환한다")
    void refresh_blankRefreshToken_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/admin/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));

        verifyNoInteractions(adminRefreshTokenService);
    }

    @Test
    @DisplayName("쓸 수 없는 리프레시 토큰으로 재발급하면 재로그인 필요를 알린다")
    void refresh_unusableRefreshToken_returnsReauthenticationRequired() throws Exception {
        // Given
        given(adminRefreshTokenService.refresh("expired-refresh-token"))
                .willThrow(new UnauthorizedException(
                        ErrorCode.REAUTHENTICATION_REQUIRED,
                        "다시 로그인해 주세요."));

        // When & Then
        mockMvc.perform(post("/api/v1/admin/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "expired-refresh-token"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("REAUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("액세스 토큰 없이 리프레시 토큰만으로 로그아웃하면 그 기기의 계보를 폐기한다")
    void logout_refreshToken_revokesLineageWithoutAccessToken() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/admin/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "admin-refresh-token"
                                }
                                """))
                .andExpect(status().isNoContent());

        then(adminRefreshTokenService).should().logout("admin-refresh-token");
        then(adminActorResolver).should(never()).resolve();
    }

    @Test
    @DisplayName("로그아웃 요청의 리프레시 토큰이 비어 있으면 400을 반환한다")
    void logout_blankRefreshToken_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/admin/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));

        verifyNoInteractions(adminRefreshTokenService);
    }
}
