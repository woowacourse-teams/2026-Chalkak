package com.chalkak.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;

import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.infrastructure.infra.access.JwtAccessTokenProvider;
import com.chalkak.backend.auth.repository.PendingAppleAuthorizationRepository;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import com.chalkak.backend.user.service.UserWithdrawalService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SocialLoginServiceTest extends IntegrationTestSupport {

    private static final String ID_TOKEN = "google-id-token";
    private static final String RAW_NONCE = "raw-nonce";
    private static final String SUBJECT = "google-subject";
    private static final String APPLE_ID_TOKEN = "apple-id-token";
    private static final String APPLE_SUBJECT = "apple-subject";
    private static final String AUTHORIZATION_CODE = "apple-authorization-code";

    @Autowired
    private SocialLoginService socialLoginService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private UserWithdrawalService userWithdrawalService;

    @Autowired
    private SocialIdentityFingerprintEncoder fingerprintEncoder;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean(name = "googleIdTokenVerifier")
    private IdTokenVerifier googleIdTokenVerifier;

    @MockitoSpyBean(name = "appleIdTokenVerifier")
    private IdTokenVerifier appleIdTokenVerifier;

    @MockitoBean
    private AppleTokenClient appleTokenClient;

    @MockitoBean
    private AppleAuthorizationCipher authorizationCipher;

    @Autowired
    private PendingAppleAuthorizationRepository pendingAuthorizationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("등록된 소셜 계정으로 로그인하면 기존 회원 식별자를 반환한다")
    void login_existingSocialAccount_returnsLoginSuccess() {
        // Given
        willReturn(identity())
                .given(googleIdTokenVerifier)
                .verify(ID_TOKEN, RAW_NONCE);
        User user = userRepository.save(UserFixture.create());
        socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                subjectHmac()));
        flushAndClear();

        // When
        SocialLoginResult result = socialLoginService.login(
                SocialProvider.GOOGLE,
                ID_TOKEN,
                RAW_NONCE,
                null);

        // Then
        assertThat(result.status()).isEqualTo(SocialLoginStatus.LOGIN_SUCCESS);
        assertThat(result.userId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("로그인에 성공하면 리프레시 토큰 계보를 하나 남기고 평문은 저장하지 않는다")
    void login_existingSocialAccount_issuesRefreshToken() {
        // Given
        willReturn(identity())
                .given(googleIdTokenVerifier)
                .verify(ID_TOKEN, RAW_NONCE);
        User user = userRepository.save(UserFixture.create());
        socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                subjectHmac()));
        flushAndClear();

        // When
        SocialLoginResult result = socialLoginService.login(
                SocialProvider.GOOGLE,
                ID_TOKEN,
                RAW_NONCE,
                null);

        // Then
        assertThat(result.refreshToken().value()).isNotBlank();
        List<String> storedHashes = jdbcTemplate.queryForList(
                "SELECT token_hash FROM user_refresh_tokens WHERE user_id = ?",
                String.class,
                user.getId());
        assertThat(storedHashes).hasSize(1);
        assertThat(storedHashes.getFirst())
                .isNotEqualTo(result.refreshToken().value())
                .matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("등록되지 않은 소셜 계정으로 로그인하면 회원가입 필요 상태를 반환한다")
    void login_newSocialAccount_returnsSignUpRequired() {
        // Given
        willReturn(identity())
                .given(googleIdTokenVerifier)
                .verify(ID_TOKEN, RAW_NONCE);

        // When
        SocialLoginResult result = socialLoginService.login(
                SocialProvider.GOOGLE,
                ID_TOKEN,
                RAW_NONCE,
                null);

        // Then
        assertThat(result.status()).isEqualTo(SocialLoginStatus.SIGN_UP_REQUIRED);
        assertThat(result.userId()).isNull();
    }

    @Test
    @DisplayName("탈퇴한 회원이 동일한 소셜 계정으로 로그인하면 회원가입 필요 상태를 반환한다")
    void login_withdrawnUser_returnsSignUpRequired() {
        // Given
        willReturn(identity())
                .given(googleIdTokenVerifier)
                .verify(ID_TOKEN, RAW_NONCE);
        User user = userRepository.save(UserFixture.create());
        socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                subjectHmac()));
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // When
        SocialLoginResult result = socialLoginService.login(
                SocialProvider.GOOGLE,
                ID_TOKEN,
                RAW_NONCE,
                null);

        // Then
        assertThat(result.status()).isEqualTo(SocialLoginStatus.SIGN_UP_REQUIRED);
        assertThat(result.userId()).isNull();
    }

    @Test
    @DisplayName("차단 회원이 탈퇴한 뒤 동일한 소셜 계정으로 로그인하면 거부한다")
    void login_withdrawnBannedUser_throwsForbiddenException() {
        // Given
        willReturn(identity())
                .given(googleIdTokenVerifier)
                .verify(ID_TOKEN, RAW_NONCE);
        User user = userRepository.save(UserFixture.create());
        socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                subjectHmac()));
        user.ban();
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // When & Then
        assertThatThrownBy(() -> socialLoginService.login(
                SocialProvider.GOOGLE,
                ID_TOKEN,
                RAW_NONCE,
                null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("탈퇴한 차단 소셜 계정입니다.");
    }

    @Test
    @DisplayName("차단된 회원에 연결된 소셜 계정으로 로그인하면 액세스 토큰을 발급한다")
    void login_bannedUser_issuesAccessToken() {
        // Given
        willReturn(identity())
                .given(googleIdTokenVerifier)
                .verify(ID_TOKEN, RAW_NONCE);
        User user = UserFixture.create();
        user.ban();
        userRepository.save(user);
        socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                subjectHmac()));
        flushAndClear();

        // When
        SocialLoginResult result = socialLoginService.login(
                SocialProvider.GOOGLE,
                ID_TOKEN,
                RAW_NONCE,
                null);

        // Then
        Jwt jwt = accessTokenProvider.jwtDecoder()
                .decode(result.accessToken().value());
        assertThat(result.status()).isEqualTo(SocialLoginStatus.LOGIN_SUCCESS);
        assertThat(result.userId()).isEqualTo(user.getId());
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
    }

    @Test
    @DisplayName("등록된 소셜 계정으로 로그인하면 회원 식별자를 담은 액세스 토큰을 발급한다")
    void login_existingSocialAccount_issuesAccessToken() {
        // Given
        willReturn(identity())
                .given(googleIdTokenVerifier)
                .verify(ID_TOKEN, RAW_NONCE);
        User user = userRepository.save(UserFixture.create());
        socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                subjectHmac()));
        flushAndClear();

        // When
        SocialLoginResult result = socialLoginService.login(
                SocialProvider.GOOGLE,
                ID_TOKEN,
                RAW_NONCE,
                null);

        // Then
        Jwt jwt = accessTokenProvider.jwtDecoder()
                .decode(result.accessToken().value());
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(result.accessToken().expiresIn()).isPositive();
    }

    @Test
    @DisplayName("회원가입이 필요한 소셜 계정에는 액세스 토큰을 발급하지 않는다")
    void login_newSocialAccount_doesNotIssueAccessToken() {
        // Given
        willReturn(identity())
                .given(googleIdTokenVerifier)
                .verify(ID_TOKEN, RAW_NONCE);

        // When
        SocialLoginResult result = socialLoginService.login(
                SocialProvider.GOOGLE,
                ID_TOKEN,
                RAW_NONCE,
                null);

        // Then
        assertThat(result.accessToken()).isNull();
    }

    @Test
    @DisplayName("신규 Apple 사용자는 공통 로그인으로 가입 필요를 받고 Refresh Token이 보관된다")
    void login_newAppleAccount_storesPendingAuthorization() {
        // Given
        VerifiedSocialIdentity appleIdentity = appleIdentity();
        willReturn(appleIdentity)
                .given(appleIdTokenVerifier)
                .verify(APPLE_ID_TOKEN, RAW_NONCE);
        given(appleTokenClient.exchangeAuthorizationCode(AUTHORIZATION_CODE))
                .willReturn(new AppleTokenExchangeResult(
                        "exchanged-apple-id-token",
                        "apple-refresh-token"));
        willReturn(appleIdentity)
                .given(appleIdTokenVerifier)
                .verify("exchanged-apple-id-token", RAW_NONCE);
        given(authorizationCipher.encrypt("apple-refresh-token"))
                .willReturn("encrypted-apple-refresh-token");

        // When
        SocialLoginResult result = socialLoginService.login(
                SocialProvider.APPLE,
                APPLE_ID_TOKEN,
                RAW_NONCE,
                AUTHORIZATION_CODE);
        flushAndClear();

        // Then
        assertThat(result.status()).isEqualTo(SocialLoginStatus.SIGN_UP_REQUIRED);
        assertThat(pendingAuthorizationRepository
                .findLatestUnexpiredBySubjectHmacForUpdate(
                        fingerprintEncoder.encode(SocialProvider.APPLE, APPLE_SUBJECT),
                        Instant.now())
                .orElseThrow()
                .getEncryptedRefreshToken())
                .isEqualTo("encrypted-apple-refresh-token");
    }

    private VerifiedSocialIdentity appleIdentity() {
        return new VerifiedSocialIdentity(
                SocialProvider.APPLE,
                APPLE_SUBJECT,
                "user@privaterelay.appleid.com");
    }

    private VerifiedSocialIdentity identity() {
        return new VerifiedSocialIdentity(
                SocialProvider.GOOGLE,
                SUBJECT,
                "user@chalkak.test");
    }

    private String subjectHmac() {
        return fingerprintEncoder.encode(SocialProvider.GOOGLE, SUBJECT);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
