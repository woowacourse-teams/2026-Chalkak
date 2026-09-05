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
    @DisplayName("같은 소셜 계정에 인증 정보를 중복 저장할 수 없다")
    void save_duplicateSocialAccount_throwsException() {
        // Given
        SocialAccount socialAccount = saveAppleSocialAccount();
        appleAuthorizationRepository.save(AppleAuthorization.create(
                socialAccount,
                ENCRYPTED_REFRESH_TOKEN));
        entityManager.flush();

        // When & Then
        assertThatThrownBy(() -> {
            appleAuthorizationRepository.save(AppleAuthorization.create(
                    socialAccount,
                    "another-encrypted-refresh-token"));
            entityManager.flush();
        })
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasRootCauseInstanceOf(SQLException.class);
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
