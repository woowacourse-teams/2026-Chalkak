package com.chalkak.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chalkak.backend.auth.domain.AppleAuthorization;
import com.chalkak.backend.auth.domain.AppleSignupAuthorization;
import com.chalkak.backend.auth.domain.IssuedSocialSignupToken;
import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.repository.AppleAuthorizationRepository;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AppleLoginServiceTest extends IntegrationTestSupport {

    private static final String ID_TOKEN = "apple-id-token";
    private static final String EXCHANGED_ID_TOKEN = "exchanged-apple-id-token";
    private static final String AUTHORIZATION_CODE = "apple-authorization-code";
    private static final String RAW_NONCE = "raw-nonce";
    private static final String SUBJECT = "apple-subject";
    private static final String CLIENT_ID = "com.chalkak.ios";
    private static final String REFRESH_TOKEN = "apple-refresh-token";
    private static final String ENCRYPTED_REFRESH_TOKEN = "encrypted-refresh-token";

    @Autowired
    private AppleLoginService appleLoginService;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private AppleAuthorizationRepository appleAuthorizationRepository;

    @Autowired
    private SocialIdentityFingerprintEncoder fingerprintEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private AppleIdTokenVerifier appleIdTokenVerifier;

    @MockitoBean
    private AppleTokenClient appleTokenClient;

    @MockitoBean
    private AppleRefreshTokenCipher refreshTokenCipher;

    @MockitoBean
    private SocialSignupTokenIssuer socialSignupTokenIssuer;

    @MockitoBean
    private SocialSignupTokenVerifier socialSignupTokenVerifier;

    @Test
    @DisplayName("기존 Apple 회원은 authorizationCode를 교환하지 않고 Access Token으로 로그인한다")
    void login_existingAccount_returnsAccessTokenWithoutExchange() {
        // Given
        SocialAccount socialAccount = saveAppleSocialAccount(UserFixture.create());
        givenVerifiedIdToken();

        // When
        AppleLoginResult result = appleLoginService.login(
                ID_TOKEN,
                AUTHORIZATION_CODE,
                RAW_NONCE);

        // Then
        assertThat(result.status()).isEqualTo(SocialLoginStatus.LOGIN_SUCCESS);
        assertThat(result.userId()).isEqualTo(socialAccount.getUser().getId());
        assertThat(result.accessToken()).isNotNull();
        assertThat(result.refreshToken()).isNotNull();
        assertThat(result.refreshToken().value()).isNotBlank();
        assertThat(result.refreshToken().expiresIn()).isPositive();
        assertThat(result.signupToken()).isNull();
        verifyNoInteractions(appleTokenClient, refreshTokenCipher);
    }

    @Test
    @DisplayName("신규 Apple 사용자에게는 리프레시 토큰을 발급하지 않는다")
    void login_newAccount_issuesNoRefreshToken() {
        // Given
        givenSuccessfulAppleAuthentication();
        given(socialSignupTokenIssuer.issueApple(any(), any(UUID.class), any()))
                .willReturn(new IssuedSocialSignupToken("apple-signup-token"));

        // When
        AppleLoginResult result = appleLoginService.login(
                ID_TOKEN,
                AUTHORIZATION_CODE,
                RAW_NONCE);

        // Then
        assertThat(result.status()).isEqualTo(SocialLoginStatus.SIGN_UP_REQUIRED);
        assertThat(result.refreshToken()).isNull();
    }

    @Test
    @DisplayName("기존 Apple 회원이 다시 로그인해도 저장된 RT는 그대로 유지된다")
    void login_existingAuthorization_keepsStoredRefreshToken() {
        // Given
        // 로그인마다 교환하면 Apple에 폐기할 수 없는 grant가 하나씩 쌓이므로, 가입 시점에
        // 저장한 RT를 그대로 두고 새 인증 정보를 만들지 않는다.
        SocialAccount socialAccount = saveAppleSocialAccount(UserFixture.create());
        appleAuthorizationRepository.save(AppleAuthorization.create(
                socialAccount,
                CLIENT_ID,
                ENCRYPTED_REFRESH_TOKEN));
        entityManager.flush();
        entityManager.clear();
        givenVerifiedIdToken();

        // When
        appleLoginService.login(ID_TOKEN, AUTHORIZATION_CODE, RAW_NONCE);
        entityManager.flush();
        entityManager.clear();

        // Then
        List<AppleAuthorization> authorizations = appleAuthorizationRepository
                .findAllBySocialAccountId(socialAccount.getId());
        assertThat(authorizations).hasSize(1);
        assertThat(authorizations.getFirst().getEncryptedRefreshToken())
                .isEqualTo(ENCRYPTED_REFRESH_TOKEN);
        verifyNoInteractions(appleTokenClient);
    }

    @Test
    @DisplayName("신규 Apple 사용자는 암호화된 RT가 포함된 signupToken을 받는다")
    void login_newAccount_returnsSignupToken() {
        // Given
        VerifiedSocialIdentity identity = givenSuccessfulAppleAuthentication();
        IssuedSocialSignupToken issuedToken =
                new IssuedSocialSignupToken("apple-signup-token");
        given(socialSignupTokenIssuer.issueApple(
                any(),
                any(UUID.class),
                any()))
                .willReturn(issuedToken);

        // When
        AppleLoginResult result = appleLoginService.login(
                ID_TOKEN,
                AUTHORIZATION_CODE,
                RAW_NONCE);

        // Then
        ArgumentCaptor<AppleSignupAuthorization> authorizationCaptor =
                ArgumentCaptor.forClass(AppleSignupAuthorization.class);
        verify(socialSignupTokenIssuer).issueApple(
                org.mockito.ArgumentMatchers.eq(identity),
                any(UUID.class),
                authorizationCaptor.capture());
        assertThat(result.status()).isEqualTo(SocialLoginStatus.SIGN_UP_REQUIRED);
        assertThat(result.userId()).isNull();
        assertThat(result.accessToken()).isNull();
        assertThat(result.signupToken()).isEqualTo(issuedToken);
        assertThat(authorizationCaptor.getValue().clientId()).isEqualTo(CLIENT_ID);
        assertThat(authorizationCaptor.getValue().encryptedRefreshToken())
                .isEqualTo(ENCRYPTED_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("클라이언트와 Apple 서버의 ID Token 사용자가 다르면 로그인을 거부한다")
    void login_differentTokenSubjects_throwsUnauthorizedException() {
        // Given
        VerifiedSocialIdentity identity = identity(SUBJECT);
        given(appleIdTokenVerifier.verify(ID_TOKEN, RAW_NONCE))
                .willReturn(identity);
        given(appleTokenClient.exchangeAuthorizationCode(AUTHORIZATION_CODE))
                .willReturn(exchangeResult());
        given(appleIdTokenVerifier.verify(EXCHANGED_ID_TOKEN, RAW_NONCE))
                .willReturn(identity("different-apple-subject"));

        // When & Then
        assertThatThrownBy(() -> appleLoginService.login(
                ID_TOKEN,
                AUTHORIZATION_CODE,
                RAW_NONCE))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Apple 로그인 사용자 정보가 일치하지 않습니다.");
        verifyNoInteractions(refreshTokenCipher, socialSignupTokenIssuer);
    }

    @Test
    @DisplayName("탈퇴하지 않은 차단 Apple 회원은 로그인에 성공한다")
    void login_bannedAccount_returnsAccessToken() {
        // Given
        User bannedUser = UserFixture.create();
        bannedUser.ban();
        SocialAccount socialAccount = saveAppleSocialAccount(bannedUser);
        givenVerifiedIdToken();

        // When
        AppleLoginResult result = appleLoginService.login(
                ID_TOKEN,
                AUTHORIZATION_CODE,
                RAW_NONCE);

        // Then
        assertThat(result.status()).isEqualTo(SocialLoginStatus.LOGIN_SUCCESS);
        assertThat(result.userId()).isEqualTo(socialAccount.getUser().getId());
        assertThat(result.accessToken()).isNotNull();
    }

    @Test
    @DisplayName("탈퇴한 차단 Apple 회원은 authorizationCode를 교환하기 전에 로그인을 거부한다")
    void login_withdrawnBannedAccount_throwsForbiddenExceptionBeforeExchange() {
        // Given
        User bannedUser = UserFixture.create();
        bannedUser.ban();
        bannedUser.withdraw();
        saveAppleSocialAccount(bannedUser);
        given(appleIdTokenVerifier.verify(ID_TOKEN, RAW_NONCE))
                .willReturn(identity(SUBJECT));

        // When & Then
        assertThatThrownBy(() -> appleLoginService.login(
                ID_TOKEN,
                AUTHORIZATION_CODE,
                RAW_NONCE))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("탈퇴한 차단 소셜 계정입니다.");
        verifyNoInteractions(appleTokenClient, refreshTokenCipher);
    }

    @Test
    @DisplayName("신규 사용자의 Apple 토큰 교환이 실패하면 signupToken을 발급하지 않는다")
    void login_newAccountTokenExchangeFailure_issuesNoSignupToken() {
        // Given
        given(appleIdTokenVerifier.verify(ID_TOKEN, RAW_NONCE))
                .willReturn(identity(SUBJECT));
        given(appleTokenClient.exchangeAuthorizationCode(AUTHORIZATION_CODE))
                .willThrow(new IllegalStateException("Apple 통신 실패"));

        // When & Then
        assertThatThrownBy(() -> appleLoginService.login(
                ID_TOKEN,
                AUTHORIZATION_CODE,
                RAW_NONCE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Apple 통신 실패");
        verifyNoInteractions(refreshTokenCipher, socialSignupTokenIssuer);
    }

    private void givenVerifiedIdToken() {
        given(appleIdTokenVerifier.verify(ID_TOKEN, RAW_NONCE))
                .willReturn(identity(SUBJECT));
    }

    private VerifiedSocialIdentity givenSuccessfulAppleAuthentication() {
        VerifiedSocialIdentity identity = identity(SUBJECT);
        given(appleIdTokenVerifier.verify(ID_TOKEN, RAW_NONCE))
                .willReturn(identity);
        given(appleTokenClient.exchangeAuthorizationCode(AUTHORIZATION_CODE))
                .willReturn(exchangeResult());
        given(appleIdTokenVerifier.verify(EXCHANGED_ID_TOKEN, RAW_NONCE))
                .willReturn(identity);
        given(refreshTokenCipher.encrypt(REFRESH_TOKEN))
                .willReturn(ENCRYPTED_REFRESH_TOKEN);
        return identity;
    }

    private AppleTokenExchangeResult exchangeResult() {
        return new AppleTokenExchangeResult(
                EXCHANGED_ID_TOKEN,
                REFRESH_TOKEN,
                CLIENT_ID);
    }

    private VerifiedSocialIdentity identity(String subject) {
        return new VerifiedSocialIdentity(
                SocialProvider.APPLE,
                subject,
                "user@privaterelay.appleid.com");
    }

    private SocialAccount saveAppleSocialAccount(User user) {
        userRepository.save(user);
        SocialAccount socialAccount = socialAccountRepository.save(
                SocialAccount.create(
                        user,
                        SocialProvider.APPLE,
                        subjectHmac()));
        entityManager.flush();
        entityManager.clear();
        return socialAccount;
    }

    private String subjectHmac() {
        return fingerprintEncoder.encode(SocialProvider.APPLE, SUBJECT);
    }
}
