package com.chalkak.backend.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Persistence;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SocialAccountRepositoryTest extends IntegrationTestSupport {

    private static final String GOOGLE_SUBJECT_HMAC =
            "921c5d35312df654eaa8ec114fd1de5a156cbcc64b23ddb6a709a9423f90c218";
    private static final String KAKAO_SUBJECT_HMAC =
            "73bce40e3e8b39018d68b14b38454b28892c1b50c93b143ce36b5406949542ba";

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("provider와 subject HMAC으로 소셜 계정과 회원을 함께 조회한다")
    void findByProviderAndSubjectHmac_existingAccount_loadsUser() {
        // Given
        User user = userRepository.save(createUser());
        socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                GOOGLE_SUBJECT_HMAC));
        entityManager.flush();
        entityManager.clear();

        // When
        SocialAccount socialAccount = socialAccountRepository
                .findByProviderAndSubjectHmac(
                        SocialProvider.GOOGLE,
                        GOOGLE_SUBJECT_HMAC)
                .orElseThrow();

        // Then
        assertThat(socialAccount.getSubjectHmac()).isEqualTo(GOOGLE_SUBJECT_HMAC);
        assertThat(Persistence.getPersistenceUtil().isLoaded(socialAccount.getUser()))
                .isTrue();
    }

    @Test
    @DisplayName("동일한 제공자의 동일한 subject HMAC을 여러 회원에게 연결할 수 없다")
    void save_duplicateProviderAndSubjectHmac_throwsException() {
        // Given
        User firstUser = userRepository.save(createUser());
        User secondUser = userRepository.save(createUser());
        socialAccountRepository.save(
                SocialAccount.create(
                        firstUser,
                        SocialProvider.GOOGLE,
                        GOOGLE_SUBJECT_HMAC));
        entityManager.flush();

        // When & Then
        assertThatThrownBy(() -> socialAccountRepository.save(
                SocialAccount.create(
                        secondUser,
                        SocialProvider.GOOGLE,
                        GOOGLE_SUBJECT_HMAC)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasRootCauseInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("한 회원에게 여러 소셜 계정을 연결할 수 없다")
    void save_duplicateUser_throwsException() {
        // Given
        User user = userRepository.save(createUser());
        socialAccountRepository.save(
                SocialAccount.create(
                        user,
                        SocialProvider.GOOGLE,
                        GOOGLE_SUBJECT_HMAC));
        entityManager.flush();

        // When & Then
        assertThatThrownBy(() -> socialAccountRepository.save(
                SocialAccount.create(
                        user,
                        SocialProvider.KAKAO,
                        KAKAO_SUBJECT_HMAC)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasRootCauseInstanceOf(SQLException.class);
    }

    private User createUser() {
        String unique = UUID.randomUUID().toString();
        return User.create(
                null,
                new SignatureStorageKeys(
                        "signatures/original/" + unique,
                        "signatures/thumbnail/" + unique));
    }
}
