package com.chalkak.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

import com.chalkak.backend.auth.domain.AppleAuthorization;
import com.chalkak.backend.auth.domain.PendingAppleAuthorization;
import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.infrastructure.infra.access.JwtAccessTokenProvider;
import com.chalkak.backend.auth.repository.AppleAuthorizationRepository;
import com.chalkak.backend.auth.repository.PendingAppleAuthorizationRepository;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import com.chalkak.backend.user.service.UserWithdrawalService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private static final String EXCHANGED_ID_TOKEN = "exchanged-apple-id-token";
    private static final String REFRESH_TOKEN = "apple-refresh-token";
    private static final String ENCRYPTED_REFRESH_TOKEN = "encrypted-apple-refresh-token";
    /** AppleSignupAuthorizationService.PENDING_AUTHORIZATION_EXPIRATION */
    private static final Duration PENDING_EXPIRATION = Duration.ofMinutes(15);

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

    @Autowired
    private AppleAuthorizationRepository appleAuthorizationRepository;

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
    @DisplayName("기존 Apple 회원은 authorizationCode를 교환하지 않고 로그인한다")
    void login_existingAppleAccount_returnsLoginSuccessWithoutExchange() {
        // Given
        SocialAccount socialAccount = saveAppleSocialAccount(UserFixture.create());
        givenVerifiedAppleIdToken();

        // When
        SocialLoginResult result = loginWithApple();

        // Then
        assertThat(result.status()).isEqualTo(SocialLoginStatus.LOGIN_SUCCESS);
        assertThat(result.userId()).isEqualTo(socialAccount.getUser().getId());
        assertThat(result.accessToken()).isNotNull();
        assertThat(result.refreshToken().value()).isNotBlank();
        verifyNoInteractions(appleTokenClient, authorizationCipher);
    }

    @Test
    @DisplayName("기존 Apple 회원이 다시 로그인해도 저장된 RT는 그대로 유지된다")
    void login_existingAppleAuthorization_keepsStoredRefreshToken() {
        // Given
        // 로그인마다 교환하면 Apple에 폐기할 수 없는 grant가 하나씩 쌓이므로, 가입 시점에
        // 저장한 RT를 그대로 두고 새 인증 정보를 만들지 않는다.
        SocialAccount socialAccount = saveAppleSocialAccount(UserFixture.create());
        appleAuthorizationRepository.save(AppleAuthorization.create(
                socialAccount,
                ENCRYPTED_REFRESH_TOKEN));
        flushAndClear();
        givenVerifiedAppleIdToken();

        // When
        loginWithApple();
        flushAndClear();

        // Then
        List<AppleAuthorization> authorizations = appleAuthorizationRepository
                .findAllBySocialAccountId(socialAccount.getId());
        assertThat(authorizations).hasSize(1);
        assertThat(authorizations.getFirst().getEncryptedRefreshToken())
                .isEqualTo(ENCRYPTED_REFRESH_TOKEN);
        verifyNoInteractions(appleTokenClient);
    }

    @Test
    @DisplayName("신규 Apple 사용자의 암호화된 RT는 신원 지문으로 보관 기간만큼 임시 저장한다")
    void login_newAppleAccount_storesPendingAuthorizationWithConfiguredExpiry() {
        // Given
        givenSuccessfulAppleAuthentication();
        Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

        // When
        SocialLoginResult result = loginWithApple();
        Instant after = Instant.now();
        flushAndClear();

        // Then
        assertThat(result.status()).isEqualTo(SocialLoginStatus.SIGN_UP_REQUIRED);
        assertThat(result.refreshToken()).isNull();
        PendingAppleAuthorization pendingAuthorization = latestPendingAuthorization();
        assertThat(pendingAuthorization.getEncryptedRefreshToken())
                .isEqualTo(ENCRYPTED_REFRESH_TOKEN);
        assertThat(pendingAuthorization.getExpiresAt()).isBetween(
                before.plus(PENDING_EXPIRATION),
                after.plus(PENDING_EXPIRATION));
    }

    @Test
    @DisplayName("클라이언트와 Apple 서버의 ID Token 사용자가 다르면 로그인을 거부한다")
    void login_differentAppleTokenSubjects_throwsUnauthorizedException() {
        // Given
        willReturn(appleIdentity(APPLE_SUBJECT))
                .given(appleIdTokenVerifier)
                .verify(APPLE_ID_TOKEN, RAW_NONCE);
        given(appleTokenClient.exchangeAuthorizationCode(AUTHORIZATION_CODE))
                .willReturn(appleExchangeResult());
        willReturn(appleIdentity("different-apple-subject"))
                .given(appleIdTokenVerifier)
                .verify(EXCHANGED_ID_TOKEN, RAW_NONCE);

        // When & Then
        assertThatThrownBy(this::loginWithApple)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Apple 로그인 사용자 정보가 일치하지 않습니다.");
        verifyNoInteractions(authorizationCipher);
    }

    @Test
    @DisplayName("탈퇴한 차단 Apple 회원은 authorizationCode를 교환하기 전에 로그인을 거부한다")
    void login_withdrawnBannedAppleAccount_throwsForbiddenExceptionBeforeExchange() {
        // Given
        User bannedUser = UserFixture.create();
        bannedUser.ban();
        bannedUser.withdraw();
        saveAppleSocialAccount(bannedUser);
        givenVerifiedAppleIdToken();

        // When & Then
        assertThatThrownBy(this::loginWithApple)
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("탈퇴한 차단 소셜 계정입니다.");
        verifyNoInteractions(appleTokenClient, authorizationCipher);
    }

    @Test
    @DisplayName("신규 사용자의 Apple 토큰 교환이 실패하면 임시 인증 정보를 저장하지 않는다")
    void login_appleTokenExchangeFailure_storesNoPendingAuthorization() {
        // Given
        givenVerifiedAppleIdToken();
        given(appleTokenClient.exchangeAuthorizationCode(AUTHORIZATION_CODE))
                .willThrow(new IllegalStateException("Apple 통신 실패"));

        // When & Then
        assertThatThrownBy(this::loginWithApple)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Apple 통신 실패");
        verifyNoInteractions(authorizationCipher);
        assertThat(pendingAuthorizationRepository
                .findLatestUnexpiredBySubjectHmacForUpdate(
                        appleSubjectHmac(),
                        Instant.now())).isEmpty();
    }

    @Test
    @DisplayName("만료 전 임시 인증 정보가 있으면 authorizationCode를 다시 교환하지 않는다")
    void login_newAppleAccountWithPendingAuthorization_skipsExchange() {
        // Given
        // 재로그인마다 교환하면 Apple에 폐기할 수 없는 grant가 하나씩 쌓인다.
        givenVerifiedAppleIdToken();
        savePendingAuthorization(
                "stored-encrypted-token",
                Instant.now().plus(Duration.ofMinutes(1)));

        // When
        SocialLoginResult result = loginWithApple();

        // Then
        assertThat(result.status()).isEqualTo(SocialLoginStatus.SIGN_UP_REQUIRED);
        verifyNoInteractions(appleTokenClient, authorizationCipher);
        assertThat(countPendingAuthorizations()).isEqualTo(1);
        assertThat(latestPendingAuthorization().getEncryptedRefreshToken())
                .isEqualTo("stored-encrypted-token");
    }

    @Test
    @DisplayName("만료 전 임시 인증 정보가 있으면 만료를 재로그인 시점 기준으로 다시 민다")
    void login_newAppleAccountWithPendingAuthorization_renewsExpiry() {
        // Given
        givenVerifiedAppleIdToken();
        savePendingAuthorization(
                "stored-encrypted-token",
                Instant.now().plus(Duration.ofMinutes(1)));
        Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

        // When
        loginWithApple();
        Instant after = Instant.now();
        flushAndClear();

        // Then
        assertThat(latestPendingAuthorization().getExpiresAt()).isBetween(
                before.plus(PENDING_EXPIRATION),
                after.plus(PENDING_EXPIRATION));
    }

    @Test
    @DisplayName("임시 인증 정보가 만료됐으면 다시 교환해 보관하고 이전 행은 남긴다")
    void login_newAppleAccountWithExpiredPendingAuthorization_exchangesAgain() {
        // Given
        // 이전 행의 RT는 아직 Apple에 폐기되지 않았으므로 정리 스케줄러가 처리하도록 남긴다.
        givenSuccessfulAppleAuthentication();
        savePendingAuthorization(
                "expired-encrypted-token",
                Instant.now().minusSeconds(1));

        // When
        loginWithApple();
        flushAndClear();

        // Then
        verify(appleTokenClient).exchangeAuthorizationCode(AUTHORIZATION_CODE);
        assertThat(countPendingAuthorizations()).isEqualTo(2);
        assertThat(latestPendingAuthorization().getEncryptedRefreshToken())
                .isEqualTo(ENCRYPTED_REFRESH_TOKEN);
    }

    private SocialLoginResult loginWithApple() {
        return socialLoginService.login(
                SocialProvider.APPLE,
                APPLE_ID_TOKEN,
                RAW_NONCE,
                AUTHORIZATION_CODE);
    }

    private void givenVerifiedAppleIdToken() {
        willReturn(appleIdentity(APPLE_SUBJECT))
                .given(appleIdTokenVerifier)
                .verify(APPLE_ID_TOKEN, RAW_NONCE);
    }

    private void givenSuccessfulAppleAuthentication() {
        VerifiedSocialIdentity identity = appleIdentity(APPLE_SUBJECT);
        willReturn(identity)
                .given(appleIdTokenVerifier)
                .verify(APPLE_ID_TOKEN, RAW_NONCE);
        given(appleTokenClient.exchangeAuthorizationCode(AUTHORIZATION_CODE))
                .willReturn(appleExchangeResult());
        willReturn(identity)
                .given(appleIdTokenVerifier)
                .verify(EXCHANGED_ID_TOKEN, RAW_NONCE);
        given(authorizationCipher.encrypt(REFRESH_TOKEN))
                .willReturn(ENCRYPTED_REFRESH_TOKEN);
    }

    private AppleTokenExchangeResult appleExchangeResult() {
        return new AppleTokenExchangeResult(EXCHANGED_ID_TOKEN, REFRESH_TOKEN);
    }

    private VerifiedSocialIdentity appleIdentity(String subject) {
        return new VerifiedSocialIdentity(
                SocialProvider.APPLE,
                subject,
                "user@privaterelay.appleid.com");
    }

    private SocialAccount saveAppleSocialAccount(User user) {
        userRepository.save(user);
        SocialAccount socialAccount = socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.APPLE,
                appleSubjectHmac()));
        flushAndClear();
        return socialAccount;
    }

    private void savePendingAuthorization(
            String encryptedRefreshToken,
            Instant expiresAt
    ) {
        pendingAuthorizationRepository.save(PendingAppleAuthorization.create(
                appleSubjectHmac(),
                encryptedRefreshToken,
                expiresAt));
        flushAndClear();
    }

    private PendingAppleAuthorization latestPendingAuthorization() {
        return pendingAuthorizationRepository
                .findLatestUnexpiredBySubjectHmacForUpdate(
                        appleSubjectHmac(),
                        Instant.now())
                .orElseThrow();
    }

    private long countPendingAuthorizations() {
        return entityManager.createQuery(
                        "SELECT count(a) FROM PendingAppleAuthorization a",
                        Long.class)
                .getSingleResult();
    }

    private String appleSubjectHmac() {
        return fingerprintEncoder.encode(SocialProvider.APPLE, APPLE_SUBJECT);
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
