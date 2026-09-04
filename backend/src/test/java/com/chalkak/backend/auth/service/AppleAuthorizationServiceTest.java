package com.chalkak.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
import java.util.List;
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
    @DisplayName("Apple 회원의 인증 정보 스냅샷을 조회한다")
    void findAuthorizationSnapshots_appleUser_returnsSnapshot() {
        // Given
        User user = userRepository.save(UserFixture.create());
        SocialAccount socialAccount = saveSocialAccount(user, SocialProvider.APPLE);
        AppleAuthorization authorization = appleAuthorizationRepository.save(
                AppleAuthorization.create(
                        socialAccount,
                        CLIENT_ID,
                        "encrypted-refresh-token"));
        flushAndClear();

        // When
        List<AppleAuthorizationSnapshot> snapshots =
                appleAuthorizationService.findAuthorizationSnapshots(user.getId());

        // Then
        assertThat(snapshots).containsExactly(new AppleAuthorizationSnapshot(
                authorization.getId(),
                CLIENT_ID,
                "encrypted-refresh-token"));
    }

    @Test
    @DisplayName("한 회원의 Client ID별 인증 정보 스냅샷을 모두 조회한다")
    void findAuthorizationSnapshots_multipleClientIds_returnsEverySnapshot() {
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
        List<AppleAuthorizationSnapshot> snapshots =
                appleAuthorizationService.findAuthorizationSnapshots(user.getId());

        // Then
        assertThat(snapshots)
                .extracting(
                        AppleAuthorizationSnapshot::clientId,
                        AppleAuthorizationSnapshot::encryptedRefreshToken)
                .containsExactlyInAnyOrder(
                        tuple(CLIENT_ID, "encrypted-ios-token"),
                        tuple("com.chalkak.web", "encrypted-web-token"));
    }

    @Test
    @DisplayName("Apple이 아닌 소셜 회원은 폐기할 인증 정보가 없다")
    void findAuthorizationSnapshots_nonAppleUser_returnsEmpty() {
        // Given
        User user = userRepository.save(UserFixture.create());
        saveSocialAccount(user, SocialProvider.GOOGLE);
        flushAndClear();

        // When
        List<AppleAuthorizationSnapshot> snapshots =
                appleAuthorizationService.findAuthorizationSnapshots(user.getId());

        // Then
        assertThat(snapshots).isEmpty();
    }

    @Test
    @DisplayName("소셜 계정이 없는 회원은 폐기할 인증 정보가 없다")
    void findAuthorizationSnapshots_userWithoutSocialAccount_returnsEmpty() {
        // Given
        User user = userRepository.save(UserFixture.create());
        flushAndClear();

        // When
        List<AppleAuthorizationSnapshot> snapshots =
                appleAuthorizationService.findAuthorizationSnapshots(user.getId());

        // Then
        assertThat(snapshots).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 회원은 폐기할 인증 정보가 없다")
    void findAuthorizationSnapshots_notExistingUser_returnsEmpty() {
        // Given
        UUID notExistingId = UUID.randomUUID();

        // When
        List<AppleAuthorizationSnapshot> snapshots =
                appleAuthorizationService.findAuthorizationSnapshots(notExistingId);

        // Then
        assertThat(snapshots).isEmpty();
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
