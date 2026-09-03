package com.chalkak.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.auth.domain.AppleAuthorization;
import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.repository.AppleAuthorizationRepository;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AppleAuthorizationServiceTest extends IntegrationTestSupport {

    private static final String SUBJECT = "apple-subject";
    private static final String CLIENT_ID = "com.chalkak.ios";

    @Autowired
    private AppleAuthorizationService appleAuthorizationService;

    @Autowired
    private AppleAuthorizationRepository appleAuthorizationRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private SocialIdentityFingerprintEncoder fingerprintEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Apple 회원의 암호화된 Refresh Token을 조회한다")
    void findEncryptedRefreshTokens_appleUser_returnsEncryptedToken() {
        // Given
        User user = userRepository.save(UserFixture.create());
        SocialAccount socialAccount = saveSocialAccount(user, SocialProvider.APPLE);
        appleAuthorizationRepository.save(AppleAuthorization.create(
                socialAccount,
                CLIENT_ID,
                "encrypted-refresh-token"));
        flushAndClear();

        // When
        var encryptedRefreshTokens =
                appleAuthorizationService.findEncryptedRefreshTokens(user.getId());

        // Then
        assertThat(encryptedRefreshTokens)
                .containsExactly("encrypted-refresh-token");
    }

    @Test
    @DisplayName("한 회원의 Client ID별 Refresh Token을 모두 조회한다")
    void findEncryptedRefreshTokens_multipleClientIds_returnsEveryEncryptedToken() {
        // Given
        User user = userRepository.save(UserFixture.create());
        SocialAccount socialAccount = saveSocialAccount(user, SocialProvider.APPLE);
        appleAuthorizationRepository.save(AppleAuthorization.create(
                socialAccount,
                CLIENT_ID,
                "encrypted-ios-token"));
        appleAuthorizationRepository.save(AppleAuthorization.create(
                socialAccount,
                "com.chalkak.web",
                "encrypted-web-token"));
        flushAndClear();

        // When
        var encryptedRefreshTokens =
                appleAuthorizationService.findEncryptedRefreshTokens(user.getId());

        // Then
        assertThat(encryptedRefreshTokens)
                .containsExactlyInAnyOrder("encrypted-ios-token", "encrypted-web-token");
    }

    @Test
    @DisplayName("Apple이 아닌 소셜 회원은 폐기할 Refresh Token이 없다")
    void findEncryptedRefreshTokens_nonAppleUser_returnsEmpty() {
        // Given
        User user = userRepository.save(UserFixture.create());
        saveSocialAccount(user, SocialProvider.GOOGLE);
        flushAndClear();

        // When
        var encryptedRefreshTokens =
                appleAuthorizationService.findEncryptedRefreshTokens(user.getId());

        // Then
        assertThat(encryptedRefreshTokens).isEmpty();
    }

    @Test
    @DisplayName("소셜 계정이 없는 회원은 폐기할 Refresh Token이 없다")
    void findEncryptedRefreshTokens_userWithoutSocialAccount_returnsEmpty() {
        // Given
        User user = userRepository.save(UserFixture.create());
        flushAndClear();

        // When
        var encryptedRefreshTokens =
                appleAuthorizationService.findEncryptedRefreshTokens(user.getId());

        // Then
        assertThat(encryptedRefreshTokens).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 회원은 폐기할 Refresh Token이 없다")
    void findEncryptedRefreshTokens_notExistingUser_returnsEmpty() {
        // Given
        UUID notExistingId = UUID.randomUUID();

        // When
        var encryptedRefreshTokens =
                appleAuthorizationService.findEncryptedRefreshTokens(notExistingId);

        // Then
        assertThat(encryptedRefreshTokens).isEmpty();
    }

    private SocialAccount saveSocialAccount(User user, SocialProvider provider) {
        return socialAccountRepository.save(SocialAccount.create(
                user,
                provider,
                fingerprintEncoder.encode(provider, SUBJECT)));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
