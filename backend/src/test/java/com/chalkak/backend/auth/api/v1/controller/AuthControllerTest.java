package com.chalkak.backend.auth.api.v1.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.domain.IssuedAccessToken;
import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import com.chalkak.backend.auth.domain.IssuedSocialSignupToken;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.service.AppleLoginResult;
import com.chalkak.backend.auth.service.AppleLoginService;
import com.chalkak.backend.auth.service.SocialLoginResult;
import com.chalkak.backend.auth.service.SocialLoginService;
import com.chalkak.backend.auth.service.SocialSignupResult;
import com.chalkak.backend.auth.service.SocialSignupService;
import com.chalkak.backend.auth.service.SocialSignupSignatureUploadResult;
import com.chalkak.backend.auth.service.TokenRefreshResult;
import com.chalkak.backend.auth.service.UserRefreshTokenService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.user.repository.SignatureImageUpload;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    private static final String ACCESS_TOKEN = "chalkak-access-token";
    private static final String REFRESH_TOKEN = "chalkak-refresh-token";
    private static final String ROTATED_REFRESH_TOKEN = "chalkak-rotated-refresh-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppleLoginService appleLoginService;

    @MockitoBean
    private SocialLoginService socialLoginService;

    @MockitoBean
    private SocialSignupService socialSignupService;

    @MockitoBean
    private UserRefreshTokenService userRefreshTokenService;

    @Test
    @DisplayName("기존 Apple 회원이 로그인하면 액세스 토큰을 반환한다")
    void appleLogin_existingUser_returnsLoginSuccess() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        given(appleLoginService.login(
                "apple-id-token",
                "apple-authorization-code",
                "raw-nonce"))
                .willReturn(AppleLoginResult.loginSuccess(
                        userId,
                        new IssuedAccessToken(ACCESS_TOKEN, Duration.ofHours(1))));

        // When & Then
        mockMvc.perform(post("/api/v1/auth/apple/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": "apple-id-token",
                                  "authorizationCode": "apple-authorization-code",
                                  "rawNonce": "raw-nonce"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.signupToken").doesNotExist());
    }

    @Test
    @DisplayName("신규 Apple 사용자가 로그인하면 회원가입 토큰을 반환한다")
    void appleLogin_newUser_returnsSignupToken() throws Exception {
        // Given
        given(appleLoginService.login(
                "apple-id-token",
                "apple-authorization-code",
                "raw-nonce"))
                .willReturn(AppleLoginResult.signUpRequired(
                        new IssuedSocialSignupToken("apple-signup-token")));

        // When & Then
        mockMvc.perform(post("/api/v1/auth/apple/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": "apple-id-token",
                                  "authorizationCode": "apple-authorization-code",
                                  "rawNonce": "raw-nonce"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGN_UP_REQUIRED"))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.expiresIn").doesNotExist())
                .andExpect(jsonPath("$.signupToken")
                        .value("apple-signup-token"));
    }

    @Test
    @DisplayName("Apple 로그인 필수 값이 비어 있으면 400을 반환한다")
    void appleLogin_blankRequiredValues_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/auth/apple/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": " ",
                                  "authorizationCode": " ",
                                  "rawNonce": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));

        verifyNoInteractions(appleLoginService);
    }

    @Test
    @DisplayName("Apple 회원가입 토큰으로 서명 업로드 정보를 반환한다")
    void createAppleSignupSignatureUpload_validToken_returnsUploadInformation()
            throws Exception {
        // Given
        UUID uploadId = UUID.randomUUID();
        given(socialSignupService.createAppleSignatureUpload(
                "apple-signup-token"))
                .willReturn(new SocialSignupSignatureUploadResult(
                        new SignatureImageUpload(
                                uploadId,
                                "https://s3.example.com/apple-presigned",
                                300L),
                        new IssuedSocialSignupToken("apple-signup-token")));

        // When & Then
        mockMvc.perform(post("/api/v1/auth/apple/signup/signature/uploads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "signupToken": "apple-signup-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadId").value(uploadId.toString()))
                .andExpect(jsonPath("$.uploadUrl")
                        .value("https://s3.example.com/apple-presigned"))
                .andExpect(jsonPath("$.expiresInSeconds").value(300L))
                .andExpect(jsonPath("$.signupToken")
                        .value("apple-signup-token"));
    }

    @Test
    @DisplayName("Apple 서명 업로드 요청에 회원가입 토큰이 없으면 400을 반환한다")
    void createAppleSignupSignatureUpload_missingToken_returnsBadRequest()
            throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/auth/apple/signup/signature/uploads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "회원가입 토큰은 필수입니다."));

        verifyNoInteractions(socialSignupService);
    }

    @Test
    @DisplayName("기존 회원이 소셜 로그인하면 로그인 성공 상태와 사용자 식별자를 반환한다")
    void socialLogin_existingUser_returnsLoginSuccess() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        given(socialLoginService.login(SocialProvider.GOOGLE, "google-id-token"))
                .willReturn(SocialLoginResult.loginSuccess(
                        userId,
                        new IssuedAccessToken(ACCESS_TOKEN, Duration.ofMinutes(15)),
                        new IssuedRefreshToken(REFRESH_TOKEN, Duration.ofDays(30))));

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
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").value(REFRESH_TOKEN))
                .andExpect(jsonPath("$.refreshTokenExpiresIn").value(2592000));
    }

    @Test
    @DisplayName("신규 회원이 소셜 로그인하면 회원가입 필요 상태를 반환한다")
    void socialLogin_newUser_returnsSignUpRequired() throws Exception {
        // Given
        given(socialLoginService.login(SocialProvider.GOOGLE, "google-id-token"))
                .willReturn(SocialLoginResult.signUpRequired());

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
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.expiresIn").doesNotExist());
    }

    @Test
    @DisplayName("Kakao ID Token으로 소셜 로그인할 수 있다")
    void socialLogin_kakaoProvider_returnsLoginResult() throws Exception {
        // Given
        given(socialLoginService.login(SocialProvider.KAKAO, "kakao-id-token"))
                .willReturn(SocialLoginResult.signUpRequired());

        // When & Then
        mockMvc.perform(post("/api/v1/auth/social-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "KAKAO",
                                  "idToken": "kakao-id-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGN_UP_REQUIRED"))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.expiresIn").doesNotExist());
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

    @Test
    @DisplayName("신규 회원이 유효한 ID Token을 전달하면 서명 업로드 정보를 반환한다")
    void createSocialSignupSignatureUpload_validRequest_returnsUploadInformation()
            throws Exception {
        // Given
        UUID uploadId = UUID.randomUUID();
        given(socialSignupService.createSignatureUpload(
                SocialProvider.GOOGLE,
                "google-id-token"))
                .willReturn(new SocialSignupSignatureUploadResult(
                        new SignatureImageUpload(
                                uploadId,
                                "https://s3.example.com/presigned",
                                300L),
                        new IssuedSocialSignupToken("social-signup-token")));

        // When & Then
        mockMvc.perform(post("/api/v1/auth/social-signup/signature/uploads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "GOOGLE",
                                  "idToken": "google-id-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadId").value(uploadId.toString()))
                .andExpect(jsonPath("$.uploadUrl")
                        .value("https://s3.example.com/presigned"))
                .andExpect(jsonPath("$.expiresInSeconds").value(300L))
                .andExpect(jsonPath("$.signupToken")
                        .value("social-signup-token"))
                .andExpect(jsonPath("$.signupTokenExpiresInSeconds")
                        .doesNotExist());
    }

    @Test
    @DisplayName("서명 업로드 URL 요청의 ID Token이 비어 있으면 400을 반환한다")
    void createSocialSignupSignatureUpload_blankIdToken_returnsBadRequest()
            throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/auth/social-signup/signature/uploads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "GOOGLE",
                                  "idToken": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));

        verifyNoInteractions(socialSignupService);
    }

    @Test
    @DisplayName("서명 이미지 처리가 완료된 신규 회원은 소셜 회원가입을 완료한다")
    void socialSignup_validRequest_returnsUserId() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        given(socialSignupService.signup("social-signup-token"))
                .willReturn(new SocialSignupResult(
                        userId,
                        new IssuedAccessToken(ACCESS_TOKEN, Duration.ofMinutes(15)),
                        new IssuedRefreshToken(REFRESH_TOKEN, Duration.ofDays(30))));

        // When & Then
        mockMvc.perform(post("/api/v1/auth/social-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "signupToken": "social-signup-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").value(REFRESH_TOKEN))
                .andExpect(jsonPath("$.refreshTokenExpiresIn").value(2592000));
    }

    @Test
    @DisplayName("소셜 회원가입 요청에 회원가입 토큰이 없으면 400을 반환한다")
    void socialSignup_missingSignupToken_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/auth/social-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "회원가입 토큰은 필수입니다."));

        verifyNoInteractions(socialSignupService);
    }

    @Test
    @DisplayName("소셜 회원가입 요청의 회원가입 토큰이 비어 있으면 400을 반환한다")
    void socialSignup_blankSignupToken_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/auth/social-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "signupToken": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "회원가입 토큰은 필수입니다."));

        verifyNoInteractions(socialSignupService);
    }

    @Test
    @DisplayName("서명 이미지가 처리 중이면 재시도용 에러 코드를 반환한다")
    void socialSignup_processingSignature_returnsProcessingPendingError()
            throws Exception {
        // Given
        given(socialSignupService.signup("social-signup-token"))
                .willThrow(new BusinessException(
                        ErrorCode.SIGNATURE_PROCESSING_PENDING,
                        "사인 이미지 처리 중입니다."));

        // When & Then
        mockMvc.perform(post("/api/v1/auth/social-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "signupToken": "social-signup-token"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("SIGNATURE_PROCESSING_PENDING"))
                .andExpect(jsonPath("$.message")
                        .value("사인 이미지 처리 중입니다."));
    }

    @Test
    @DisplayName("유효한 리프레시 토큰으로 재발급하면 회전된 토큰 쌍을 반환한다")
    void refresh_validRefreshToken_returnsRotatedTokens() throws Exception {
        // Given
        given(userRefreshTokenService.refresh(REFRESH_TOKEN))
                .willReturn(new TokenRefreshResult(
                        new IssuedAccessToken(ACCESS_TOKEN, Duration.ofMinutes(15)),
                        new IssuedRefreshToken(
                                ROTATED_REFRESH_TOKEN,
                                Duration.ofDays(30))));

        // When & Then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "chalkak-refresh-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").value(ROTATED_REFRESH_TOKEN))
                .andExpect(jsonPath("$.refreshTokenExpiresIn").value(2592000));
    }

    @Test
    @DisplayName("재발급 요청에 리프레시 토큰이 없으면 400을 반환한다")
    void refresh_missingRefreshToken_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "리프레시 토큰은 필수입니다."));

        verifyNoInteractions(userRefreshTokenService);
    }

    @Test
    @DisplayName("재발급 요청의 리프레시 토큰이 비어 있으면 400을 반환한다")
    void refresh_blankRefreshToken_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "리프레시 토큰은 필수입니다."));

        verifyNoInteractions(userRefreshTokenService);
    }

    /**
     * 클라이언트는 이 에러 코드를 보고 저장한 토큰을 버리고 로그인 화면으로 보낸다.
     * 액세스 토큰만 만료된 UNAUTHORIZED와 섞이면 재발급을 무한히 반복한다.
     */
    @Test
    @DisplayName("재발급에 실패하면 재로그인 필요 에러 코드와 함께 401을 반환한다")
    void refresh_rejectedRefreshToken_returnsReauthenticationRequired()
            throws Exception {
        // Given
        given(userRefreshTokenService.refresh(REFRESH_TOKEN))
                .willThrow(new UnauthorizedException(
                        ErrorCode.REAUTHENTICATION_REQUIRED,
                        "다시 로그인해 주세요."));

        // When & Then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "chalkak-refresh-token"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("REAUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("다시 로그인해 주세요."));
    }

    @Test
    @DisplayName("리프레시 토큰으로 로그아웃하면 204를 반환한다")
    void logout_validRefreshToken_returnsNoContent() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "chalkak-refresh-token"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(userRefreshTokenService).logout(REFRESH_TOKEN);
    }

    /**
     * 로그아웃은 멱등이다. 실패를 알리면 토큰의 존재 여부가 새어 나가고, 재시도한 클라이언트가
     * 로그아웃하지 못하고 막힌다.
     */
    @Test
    @DisplayName("알 수 없는 리프레시 토큰으로 로그아웃해도 204를 반환한다")
    void logout_unknownRefreshToken_returnsNoContent() throws Exception {
        // Given
        // 알 수 없는 토큰이면 서비스는 아무것도 하지 않고 정상 종료한다.

        // When & Then
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "unknown-refresh-token"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(userRefreshTokenService).logout("unknown-refresh-token");
    }

    @Test
    @DisplayName("로그아웃 요청에 리프레시 토큰이 없으면 400을 반환한다")
    void logout_missingRefreshToken_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "리프레시 토큰은 필수입니다."));

        verifyNoInteractions(userRefreshTokenService);
    }
}
