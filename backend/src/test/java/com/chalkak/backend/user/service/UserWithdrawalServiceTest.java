package com.chalkak.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chalkak.backend.auth.domain.AppleAuthorization;
import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.repository.AppleAuthorizationRepository;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.auth.service.AppleAuthorizationCipher;
import com.chalkak.backend.auth.service.AppleTokenClient;
import com.chalkak.backend.auth.service.SocialIdentityFingerprintEncoder;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
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
    private static final String WEB_CLIENT_ID = "com.chalkak.web";
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
    private AppleAuthorizationCipher authorizationCipher;

    @Test
    @DisplayName("Apple 회원이 탈퇴하면 저장된 Client ID로 Apple에 RT를 폐기하고 인증 정보를 삭제한다")
    void withdraw_appleUser_revokesRefreshTokenAndDeletesAuthorization() {
        // Given
        User user = userRepository.save(UserFixture.create());
        SocialAccount socialAccount = saveSocialAccount(user, SocialProvider.APPLE);
        UUID socialAccountId = socialAccount.getId();
        saveAuthorization(socialAccount, CLIENT_ID, ENCRYPTED_REFRESH_TOKEN);
        given(authorizationCipher.decrypt(ENCRYPTED_REFRESH_TOKEN))
                .willReturn(REFRESH_TOKEN);
        flushAndClear();

        // When
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // Then
        verify(appleTokenClient).revokeRefreshToken(REFRESH_TOKEN, CLIENT_ID);
        assertThat(userRepository.findActiveById(user.getId())).isEmpty();
        assertThat(appleAuthorizationRepository.findAllBySocialAccountId(socialAccountId))
                .isEmpty();
    }

    @Test
    @DisplayName("Client ID별 인증 정보가 여러 개면 각 Client ID로 RT를 폐기한다")
    void withdraw_multipleAuthorizations_revokesEveryRefreshTokenWithItsOwnClientId() {
        // Given
        User user = userRepository.save(UserFixture.create());
        SocialAccount socialAccount = saveSocialAccount(user, SocialProvider.APPLE);
        saveAuthorization(socialAccount, CLIENT_ID, "encrypted-ios-token");
        saveAuthorization(socialAccount, WEB_CLIENT_ID, "encrypted-web-token");
        given(authorizationCipher.decrypt("encrypted-ios-token")).willReturn("ios-token");
        given(authorizationCipher.decrypt("encrypted-web-token")).willReturn("web-token");
        flushAndClear();

        // When
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // Then
        verify(appleTokenClient).revokeRefreshToken("ios-token", CLIENT_ID);
        verify(appleTokenClient).revokeRefreshToken("web-token", WEB_CLIENT_ID);
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
        given(authorizationCipher.decrypt(ENCRYPTED_REFRESH_TOKEN))
                .willReturn(REFRESH_TOKEN);
        flushAndClear();

        // When
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // Then
        verify(appleTokenClient).revokeRefreshToken(REFRESH_TOKEN, CLIENT_ID);
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
        given(authorizationCipher.decrypt(ENCRYPTED_REFRESH_TOKEN))
                .willReturn(REFRESH_TOKEN);
        willThrow(new IllegalStateException("Apple 토큰 서버와 통신할 수 없습니다."))
                .given(appleTokenClient).revokeRefreshToken(REFRESH_TOKEN, CLIENT_ID);
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
    @DisplayName("폐기와 삭제 사이 새 Client ID로 인증이 추가되면 탈퇴가 실패하고 아무것도 삭제되지 않는다")
    void withdraw_newAuthorizationAddedDuringRevocation_failsWithoutDeletingAnything() {
        // Given
        // 이 회원은 차단되지 않아 UserService.withdraw가 소셜 계정 자체를 삭제하고, 그
        // FK CASCADE로 apple_authorizations도 함께 지워진다. 삭제 직전 대조가 이 CASCADE
        // 경로까지 막아내는지 확인하는 테스트다.
        User user = userRepository.save(UserFixture.create());
        SocialAccount socialAccount = saveSocialAccount(user, SocialProvider.APPLE);
        UUID socialAccountId = socialAccount.getId();
        saveAuthorization(socialAccount, CLIENT_ID, ENCRYPTED_REFRESH_TOKEN);
        given(authorizationCipher.decrypt(ENCRYPTED_REFRESH_TOKEN))
                .willReturn(REFRESH_TOKEN);
        willAnswer(invocation -> {
            // Apple 폐기 응답을 받은 시점과 DB 삭제 시점 사이에 다른 기기에서 웹으로
            // 새로 로그인해 인증 정보가 하나 더 생긴 상황을 흉내낸다.
            SocialAccount managed = socialAccountRepository.findByUserId(user.getId())
                    .orElseThrow();
            appleAuthorizationRepository.save(AppleAuthorization.create(
                    managed,
                    WEB_CLIENT_ID,
                    "new-encrypted-web-token"));
            entityManager.flush();
            return null;
        }).given(appleTokenClient).revokeRefreshToken(REFRESH_TOKEN, CLIENT_ID);
        flushAndClear();

        // When & Then
        assertThatThrownBy(() -> userWithdrawalService.withdraw(user.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_STATE_CHANGED)
                .hasMessage("탈퇴 처리 중 Apple 인증 정보가 변경되었습니다. 다시 시도해 주세요.");

        assertThat(userRepository.findActiveById(user.getId())).isPresent();
        assertThat(appleAuthorizationRepository.findAllBySocialAccountId(socialAccountId))
                .extracting(AppleAuthorization::getClientId)
                .containsExactlyInAnyOrder(CLIENT_ID, WEB_CLIENT_ID);
    }

    @Test
    @DisplayName("폐기와 삭제 사이 같은 Client ID의 인증이 다른 RT로 교체되면 탈퇴가 실패하고 교체된 RT를 유지한다")
    void withdraw_authorizationReplacedDuringRevocation_failsAndKeepsReplacedToken() {
        // Given
        // 개수가 그대로여서 스냅샷과 크기만으로는 구분되지 않는 상황이다. 삭제 직전 대조가
        // 개수뿐 아니라 내용까지 비교하는지 확인한다.
        User user = userRepository.save(UserFixture.create());
        SocialAccount socialAccount = saveSocialAccount(user, SocialProvider.APPLE);
        UUID socialAccountId = socialAccount.getId();
        saveAuthorization(socialAccount, CLIENT_ID, ENCRYPTED_REFRESH_TOKEN);
        given(authorizationCipher.decrypt(ENCRYPTED_REFRESH_TOKEN))
                .willReturn(REFRESH_TOKEN);
        willAnswer(invocation -> {
            SocialAccount managed = socialAccountRepository.findByUserId(user.getId())
                    .orElseThrow();
            appleAuthorizationRepository.deleteAllBySocialAccountId(socialAccountId);
            entityManager.flush();
            appleAuthorizationRepository.save(AppleAuthorization.create(
                    managed,
                    CLIENT_ID,
                    "new-encrypted-refresh-token"));
            entityManager.flush();
            return null;
        }).given(appleTokenClient).revokeRefreshToken(REFRESH_TOKEN, CLIENT_ID);
        flushAndClear();

        // When & Then
        assertThatThrownBy(() -> userWithdrawalService.withdraw(user.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_STATE_CHANGED);

        assertThat(userRepository.findActiveById(user.getId())).isPresent();
        assertThat(appleAuthorizationRepository.findAllBySocialAccountId(socialAccountId))
                .extracting(AppleAuthorization::getEncryptedRefreshToken)
                .containsExactly("new-encrypted-refresh-token");
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
        given(authorizationCipher.decrypt(ENCRYPTED_REFRESH_TOKEN))
                .willReturn(REFRESH_TOKEN);
        flushAndClear();
        userWithdrawalService.withdraw(user.getId());
        flushAndClear();

        // When & Then
        assertThatThrownBy(() -> userWithdrawalService.withdraw(user.getId()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.");

        verify(appleTokenClient).revokeRefreshToken(REFRESH_TOKEN, CLIENT_ID);
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
