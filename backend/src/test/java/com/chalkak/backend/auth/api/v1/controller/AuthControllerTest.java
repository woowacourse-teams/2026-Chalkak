package com.chalkak.backend.auth.api.v1.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.service.SocialLoginResult;
import com.chalkak.backend.auth.service.SocialLoginService;
import com.chalkak.backend.auth.service.SocialLoginStatus;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SocialLoginService socialLoginService;

    @Test
    @DisplayName("기존 회원이 소셜 로그인하면 로그인 성공 상태와 사용자 식별자를 반환한다")
    void socialLogin_existingUser_returnsLoginSuccess() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        given(socialLoginService.login(SocialProvider.GOOGLE, "google-id-token"))
                .willReturn(new SocialLoginResult(
                        SocialLoginStatus.LOGIN_SUCCESS,
                        userId));

        // When & Then
        mockMvc.perform(post("/api/v1/auth/social-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "GOOGLE",
                                  "idToken": "google-id-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    @DisplayName("신규 회원이 소셜 로그인하면 회원가입 필요 상태를 반환한다")
    void socialLogin_newUser_returnsSignUpRequired() throws Exception {
        // Given
        given(socialLoginService.login(SocialProvider.GOOGLE, "google-id-token"))
                .willReturn(new SocialLoginResult(
                        SocialLoginStatus.SIGN_UP_REQUIRED,
                        null));

        // When & Then
        mockMvc.perform(post("/api/v1/auth/social-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "GOOGLE",
                                  "idToken": "google-id-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGN_UP_REQUIRED"))
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    @DisplayName("ID Token이 비어 있으면 400을 반환한다")
    void socialLogin_blankIdToken_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/auth/social-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "GOOGLE",
                                  "idToken": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));

        verifyNoInteractions(socialLoginService);
    }

    @Test
    @DisplayName("제공자가 없으면 400을 반환한다")
    void socialLogin_missingProvider_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/auth/social-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": "google-id-token"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));

        verifyNoInteractions(socialLoginService);
    }
}
