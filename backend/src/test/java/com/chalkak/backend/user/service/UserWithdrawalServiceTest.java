package com.chalkak.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chalkak.backend.auth.domain.AppleAuthorization;
import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.repository.AppleAuthorizationRepository;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.auth.service.AppleRefreshTokenCipher;
import com.chalkak.backend.auth.service.AppleTokenClient;
import com.chalkak.backend.auth.service.SocialIdentityFingerprintEncoder;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.domain.UserStatus;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class UserWithdrawalServiceTest extends IntegrationTestSupport {

    private static final String SUBJECT = "apple-subject";
    private static final String CLIENT_ID = "com.chalkak.ios";
    private static final String ENCRYPTED_REFRESH_TOKEN = "encrypted-refresh-token";
    private static final String REFRESH_TOKEN = "apple-refresh-token";

    @Autowired
    private UserWithdrawalService userWithdrawalService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private AppleAuthorizationRepository appleAuthorizationRepository;

    @Autowired
    private SocialIdentityFingerprintEncoder fingerprintEncoder;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private AppleTokenClient appleTokenClient;

    @MockitoBean
    private AppleRefreshTokenCipher refreshTokenCipher;

    @Test
    @DisplayName("Apple 회원이 탈퇴하면 Apple에 RT를 폐기하고 인증 정보를 삭제한다")
    void withdraw_appleUser_revokesRefreshTokenAndDeletesAuthorization() {
        // Given
        User user = userRepository.save(UserFixture.create());
        SocialAccount socialAccount = saveSocialAccount(user, SocialProvider.APPLE);
        UUID socialAccountId = socialAccount.getId();
        saveAuthorization(socialAccount, CLIENT_ID, ENCRYPTED_REFRESH_TOKEN);
        given(refreshTokenCipher.decrypt(ENCRYPTED_REFRESH_TOKEN))
                .willReturn(REFRESH_TOKEN);
        flushAndClear();

        // When
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // Then
        verify(appleTokenClient).revokeRefreshToken(REFRESH_TOKEN);
        assertThat(userRepository.findActiveById(user.getId())).isEmpty();
        assertThat(appleAuthorizationRepository.findAllBySocialAccountId(socialAccountId))
                .isEmpty();
    }

    @Test
    @DisplayName("Client ID별 인증 정보가 여러 개면 RT를 각각 폐기한다")
    void withdraw_multipleAuthorizations_revokesEveryRefreshToken() {
        // Given
        User user = userRepository.save(UserFixture.create());
        SocialAccount socialAccount = saveSocialAccount(user, SocialProvider.APPLE);
        saveAuthorization(socialAccount, CLIENT_ID, "encrypted-ios-token");
        saveAuthorization(socialAccount, "com.chalkak.web", "encrypted-web-token");
        given(refreshTokenCipher.decrypt("encrypted-ios-token")).willReturn("ios-token");
        given(refreshTokenCipher.decrypt("encrypted-web-token")).willReturn("web-token");
        flushAndClear();

        // When
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // Then
        verify(appleTokenClient).revokeRefreshToken("ios-token");
        verify(appleTokenClient).revokeRefreshToken("web-token");
        assertThat(userRepository.findActiveById(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("차단된 Apple 회원이 탈퇴하면 소셜 계정은 남기고 인증 정보만 삭제한다")
    void withdraw_bannedAppleUser_preservesSocialAccountAndDeletesAuthorization() {
        // Given
        User user = userRepository.save(UserFixture.createBanned(null));
        SocialAccount socialAccount = saveSocialAccount(user, SocialProvider.APPLE);
        UUID socialAccountId = socialAccount.getId();
        saveAuthorization(socialAccount, CLIENT_ID, ENCRYPTED_REFRESH_TOKEN);
        given(refreshTokenCipher.decrypt(ENCRYPTED_REFRESH_TOKEN))
                .willReturn(REFRESH_TOKEN);
        flushAndClear();

        // When
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // Then
        verify(appleTokenClient).revokeRefreshToken(REFRESH_TOKEN);
        assertThat(appleAuthorizationRepository.findAllBySocialAccountId(socialAccountId))
                .isEmpty();
        SocialAccount preserved = socialAccountRepository
                .findByProviderAndSubjectHmac(
                        SocialProvider.APPLE,
                        subjectHmac(SocialProvider.APPLE))
                .orElseThrow();
        assertThat(preserved.getUser().isDeleted()).isTrue();
        assertThat(preserved.getUser().getStatus()).isEqualTo(UserStatus.BANNED);
    }

    @Test
    @DisplayName("Apple 회원이 아니면 폐기 요청 없이 탈퇴한다")
    void withdraw_nonAppleUser_withdrawsWithoutRevocation() {
        // Given
        User user = userRepository.save(UserFixture.create());
        saveSocialAccount(user, SocialProvider.GOOGLE);
        flushAndClear();

        // When
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // Then
        verifyNoInteractions(appleTokenClient);
        assertThat(userRepository.findActiveById(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("Apple 폐기에 실패하면 회원과 인증 정보를 그대로 두고 탈퇴하지 않는다")
    void withdraw_revocationFailure_preservesUserAndAuthorization() {
        // Given
        User user = userRepository.save(UserFixture.create());
        SocialAccount socialAccount = saveSocialAccount(user, SocialProvider.APPLE);
        UUID socialAccountId = socialAccount.getId();
        saveAuthorization(socialAccount, CLIENT_ID, ENCRYPTED_REFRESH_TOKEN);
        given(refreshTokenCipher.decrypt(ENCRYPTED_REFRESH_TOKEN))
                .willReturn(REFRESH_TOKEN);
        willThrow(new IllegalStateException("Apple 토큰 서버와 통신할 수 없습니다."))
                .given(appleTokenClient).revokeRefreshToken(REFRESH_TOKEN);
        flushAndClear();

        // When & Then
        assertThatThrownBy(() -> userWithdrawalService.withdraw(user.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Apple 토큰 서버와 통신할 수 없습니다.");

        flushAndClear();
        assertThat(userRepository.findActiveById(user.getId())).isPresent();
        assertThat(appleAuthorizationRepository.findAllBySocialAccountId(socialAccountId))
                .hasSize(1);
    }

    @Test
    @DisplayName("탈퇴할 회원이 없으면 폐기 요청 없이 예외가 발생한다")
    void withdraw_notExistingUser_throwsUnauthorizedException() {
        // Given
        UUID notExistingId = UUID.randomUUID();

        // When & Then
        assertThatThrownBy(() -> userWithdrawalService.withdraw(notExistingId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.");

        verifyNoInteractions(appleTokenClient);
    }

    @Test
    @DisplayName("이미 탈퇴한 회원은 폐기 요청 없이 예외가 발생한다")
    void withdraw_alreadyWithdrawnUser_throwsUnauthorizedException() {
        // Given
        User user = userRepository.save(UserFixture.create());
        SocialAccount socialAccount = saveSocialAccount(user, SocialProvider.APPLE);
        saveAuthorization(socialAccount, CLIENT_ID, ENCRYPTED_REFRESH_TOKEN);
        given(refreshTokenCipher.decrypt(ENCRYPTED_REFRESH_TOKEN))
                .willReturn(REFRESH_TOKEN);
        flushAndClear();
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // When & Then
        assertThatThrownBy(() -> userWithdrawalService.withdraw(user.getId()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.");

        verify(appleTokenClient).revokeRefreshToken(REFRESH_TOKEN);
    }

    private SocialAccount saveSocialAccount(User user, SocialProvider provider) {
        return socialAccountRepository.save(SocialAccount.create(
                user,
                provider,
                subjectHmac(provider)));
    }

    private void saveAuthorization(
            SocialAccount socialAccount,
            String clientId,
            String encryptedRefreshToken
    ) {
        appleAuthorizationRepository.save(AppleAuthorization.create(
                socialAccount,
                clientId,
                encryptedRefreshToken));
    }

    private String subjectHmac(SocialProvider provider) {
        return fingerprintEncoder.encode(provider, SUBJECT);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
