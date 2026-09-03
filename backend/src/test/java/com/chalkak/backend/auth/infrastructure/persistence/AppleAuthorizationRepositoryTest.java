package com.chalkak.backend.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.auth.domain.AppleAuthorization;
import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.repository.AppleAuthorizationRepository;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AppleAuthorizationRepositoryTest extends IntegrationTestSupport {

    private static final String SUBJECT_HMAC =
            "921c5d35312df654eaa8ec114fd1de5a156cbcc64b23ddb6a709a9423f90c218";
    private static final String CLIENT_ID = "com.chalkak.ios";
    private static final String ENCRYPTED_REFRESH_TOKEN = "encrypted-refresh-token";

    @Autowired
    private AppleAuthorizationRepository appleAuthorizationRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("소셜 계정과 Client ID로 Apple 인증 정보를 잠금 조회해 RT를 갱신한다")
    void findBySocialAccountIdAndClientIdForUpdate_existingAuthorization_updatesToken() {
        // Given
        SocialAccount socialAccount = saveAppleSocialAccount();
        appleAuthorizationRepository.save(AppleAuthorization.create(
                socialAccount,
                CLIENT_ID,
                ENCRYPTED_REFRESH_TOKEN));
        entityManager.flush();
        entityManager.clear();

        // When
        AppleAuthorization authorization = appleAuthorizationRepository
                .findBySocialAccountIdAndClientIdForUpdate(
                        socialAccount.getId(),
                        CLIENT_ID)
                .orElseThrow();
        authorization.updateEncryptedRefreshToken("new-encrypted-refresh-token");
        entityManager.flush();
        entityManager.clear();

        // Then
        AppleAuthorization updated = appleAuthorizationRepository
                .findBySocialAccountIdAndClientIdForUpdate(
                        socialAccount.getId(),
                        CLIENT_ID)
                .orElseThrow();
        assertThat(updated.getEncryptedRefreshToken())
                .isEqualTo("new-encrypted-refresh-token");
    }

    @Test
    @DisplayName("같은 소셜 계정과 Client ID 조합을 중복 저장할 수 없다")
    void save_duplicateSocialAccountAndClientId_throwsException() {
        // Given
        SocialAccount socialAccount = saveAppleSocialAccount();
        appleAuthorizationRepository.save(AppleAuthorization.create(
                socialAccount,
                CLIENT_ID,
                ENCRYPTED_REFRESH_TOKEN));
        entityManager.flush();

        // When & Then
        assertThatThrownBy(() -> {
            appleAuthorizationRepository.save(AppleAuthorization.create(
                    socialAccount,
                    CLIENT_ID,
                    "another-encrypted-refresh-token"));
            entityManager.flush();
        })
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasRootCauseInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("같은 소셜 계정에 서로 다른 Client ID 인증 정보를 저장할 수 있다")
    void save_differentClientIds_storesEachAuthorization() {
        // Given
        SocialAccount socialAccount = saveAppleSocialAccount();
        appleAuthorizationRepository.save(AppleAuthorization.create(
                socialAccount,
                CLIENT_ID,
                ENCRYPTED_REFRESH_TOKEN));
        appleAuthorizationRepository.save(AppleAuthorization.create(
                socialAccount,
                "com.chalkak.web",
                "web-encrypted-refresh-token"));
        entityManager.flush();
        entityManager.clear();

        // When
        List<AppleAuthorization> authorizations = appleAuthorizationRepository
                .findAllBySocialAccountId(socialAccount.getId());

        // Then
        assertThat(authorizations)
                .extracting(AppleAuthorization::getClientId)
                .containsExactlyInAnyOrder(CLIENT_ID, "com.chalkak.web");
    }

    private SocialAccount saveAppleSocialAccount() {
        User user = userRepository.save(User.create(
                null,
                new SignatureStorageKeys(
                        "signatures/original/" + UUID.randomUUID(),
                        "signatures/thumbnail/" + UUID.randomUUID())));
        return socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.APPLE,
                SUBJECT_HMAC));
    }
}
