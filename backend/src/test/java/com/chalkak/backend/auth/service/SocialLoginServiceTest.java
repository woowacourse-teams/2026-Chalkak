package com.chalkak.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willReturn;

import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.infrastructure.infra.access.JwtAccessTokenProvider;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SocialLoginServiceTest extends IntegrationTestSupport {

    private static final String ID_TOKEN = "google-id-token";
    private static final String SUBJECT = "google-subject";

    @Autowired
    private SocialLoginService socialLoginService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    @MockitoSpyBean(name = "googleIdTokenVerifier")
    private IdTokenVerifier googleIdTokenVerifier;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("등록된 소셜 계정으로 로그인하면 기존 회원 식별자를 반환한다")
    void login_existingSocialAccount_returnsLoginSuccess() {
        // Given
        willReturn(identity())
                .given(googleIdTokenVerifier)
                .verify(ID_TOKEN);
        User user = userRepository.save(UserFixture.create());
        socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                SUBJECT));
        flushAndClear();

        // When
        SocialLoginResult result = socialLoginService.login(
                SocialProvider.GOOGLE,
                ID_TOKEN);

        // Then
        assertThat(result.status()).isEqualTo(SocialLoginStatus.LOGIN_SUCCESS);
        assertThat(result.userId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("등록되지 않은 소셜 계정으로 로그인하면 회원가입 필요 상태를 반환한다")
    void login_newSocialAccount_returnsSignUpRequired() {
        // Given
        willReturn(identity())
                .given(googleIdTokenVerifier)
                .verify(ID_TOKEN);

        // When
        SocialLoginResult result = socialLoginService.login(
                SocialProvider.GOOGLE,
                ID_TOKEN);

        // Then
        assertThat(result.status()).isEqualTo(SocialLoginStatus.SIGN_UP_REQUIRED);
        assertThat(result.userId()).isNull();
    }

    @Test
    @DisplayName("탈퇴한 회원에 연결된 소셜 계정의 로그인은 거부한다")
    void login_withdrawnUser_throwsUnauthorizedException() {
        // Given
        willReturn(identity())
                .given(googleIdTokenVerifier)
                .verify(ID_TOKEN);
        User user = userRepository.save(UserFixture.create());
        socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                SUBJECT));
        user.withdraw();
        flushAndClear();

        // When & Then
        assertThatThrownBy(() -> socialLoginService.login(
                SocialProvider.GOOGLE,
                ID_TOKEN))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("탈퇴한 회원은 로그인할 수 없습니다.");
    }

    @Test
    @DisplayName("등록된 소셜 계정으로 로그인하면 회원 식별자를 담은 액세스 토큰을 발급한다")
    void login_existingSocialAccount_issuesAccessToken() {
        // Given
        willReturn(identity())
                .given(googleIdTokenVerifier)
                .verify(ID_TOKEN);
        User user = userRepository.save(UserFixture.create());
        socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                SUBJECT));
        flushAndClear();

        // When
        SocialLoginResult result = socialLoginService.login(
                SocialProvider.GOOGLE,
                ID_TOKEN);

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
                .verify(ID_TOKEN);

        // When
        SocialLoginResult result = socialLoginService.login(
                SocialProvider.GOOGLE,
                ID_TOKEN);

        // Then
        assertThat(result.accessToken()).isNull();
    }

    private VerifiedSocialIdentity identity() {
        return new VerifiedSocialIdentity(
                SocialProvider.GOOGLE,
                SUBJECT,
                "user@chalkak.test");
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
