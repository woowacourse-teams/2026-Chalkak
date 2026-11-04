package com.chalkak.backend.auth.service.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chalkak.backend.auth.domain.identity.SocialAccount;
import com.chalkak.backend.auth.domain.identity.SocialProvider;
import com.chalkak.backend.auth.domain.identity.VerifiedSocialIdentity;
import com.chalkak.backend.auth.domain.token.IssuedAccessToken;
import com.chalkak.backend.auth.domain.token.IssuedRefreshToken;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.auth.service.identity.SocialIdentityFingerprintEncoder;
import com.chalkak.backend.auth.service.token.AccessTokenIssuer;
import com.chalkak.backend.auth.service.token.UserRefreshTokenService;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.domain.UserStatus;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExistingSocialAccountLoginProcessorTest {

    private static final UUID USER_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b657a02");
    private static final String SUBJECT = "social-subject";
    private static final String SUBJECT_HMAC = "0".repeat(64);
    private static final VerifiedSocialIdentity IDENTITY = new VerifiedSocialIdentity(
            SocialProvider.GOOGLE,
            SUBJECT,
            "user@chalkak.test"
    );

    @InjectMocks
    private ExistingSocialAccountLoginProcessor processor;

    @Mock
    private SocialIdentityFingerprintEncoder fingerprintEncoder;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private AccessTokenIssuer accessTokenIssuer;

    @Mock
    private UserRefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        given(fingerprintEncoder.encode(SocialProvider.GOOGLE, SUBJECT)).willReturn(SUBJECT_HMAC);
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("탈퇴하지 않은 정상 회원과 정지 회원은 같은 로그인 ID의 토큰 발급 결과를 반환한다")
    void processIfExists_liveUser_returnsIssuedTokenPair(UserStatus status) {
        // Given
        User user = status == UserStatus.BANNED
                ? UserFixture.createBanned(USER_ID)
                : UserFixture.create(USER_ID);
        givenAccount(user);
        IssuedRefreshToken refreshToken = new IssuedRefreshToken(
                "refresh-token",
                Duration.ofDays(30),
                UUID.randomUUID()
        );
        IssuedAccessToken accessToken = new IssuedAccessToken("access-token",
                Duration.ofMinutes(15));
        given(refreshTokenService.issue(user)).willReturn(refreshToken);
        given(accessTokenIssuer.issueForSession(USER_ID, refreshToken.sessionId()))
                .willReturn(accessToken);

        // When
        SocialLoginSuccess result = processor.processIfExists(IDENTITY).orElseThrow();

        // Then
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.accessToken()).isSameAs(accessToken);
        assertThat(result.refreshToken()).isSameAs(refreshToken);
    }

    @Test
    @DisplayName("연결된 소셜 계정이 없으면 빈 결과를 반환하고 토큰을 발급하지 않는다")
    void processIfExists_noSocialAccount_returnsEmptyWithoutTokens() {
        // Given
        given(socialAccountRepository.findByProviderAndSubjectHmac(
                SocialProvider.GOOGLE, SUBJECT_HMAC)).willReturn(Optional.empty());

        // When & Then
        assertThat(processor.processIfExists(IDENTITY)).isEmpty();
        verifyNoInteractions(accessTokenIssuer, refreshTokenService);
    }

    @Test
    @DisplayName("일반 탈퇴 회원의 소셜 연결이 남아 있어도 빈 결과를 반환하고 토큰을 발급하지 않는다")
    void processIfExists_withdrawnUser_returnsEmptyWithoutTokens() {
        // Given
        User user = UserFixture.create(USER_ID);
        user.withdraw();
        givenAccount(user);

        // When & Then
        assertThat(processor.processIfExists(IDENTITY)).isEmpty();
        verifyNoInteractions(accessTokenIssuer, refreshTokenService);
    }

    @Test
    @DisplayName("탈퇴한 차단 회원의 소셜 연결은 거절하고 토큰을 발급하지 않는다")
    void processIfExists_withdrawnBannedUser_throwsWithoutTokens() {
        // Given
        User user = UserFixture.createBanned(USER_ID);
        user.withdraw();
        givenAccount(user);

        // When & Then
        assertThatThrownBy(() -> processor.processIfExists(IDENTITY))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("탈퇴한 차단 소셜 계정입니다.");
        verifyNoInteractions(accessTokenIssuer, refreshTokenService);
    }

    private void givenAccount(User user) {
        SocialAccount account = SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                SUBJECT_HMAC
        );
        given(socialAccountRepository.findByProviderAndSubjectHmac(
                SocialProvider.GOOGLE, SUBJECT_HMAC)).willReturn(Optional.of(account));
    }
}
