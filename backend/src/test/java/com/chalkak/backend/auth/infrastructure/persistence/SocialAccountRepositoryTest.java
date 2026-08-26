package com.chalkak.backend.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SocialAccountRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("동일한 제공자의 동일한 subject를 여러 회원에게 연결할 수 없다")
    void save_duplicateProviderAndSubject_throwsException() {
        // Given
        User firstUser = userRepository.save(createUser());
        User secondUser = userRepository.save(createUser());
        socialAccountRepository.save(
                SocialAccount.create(firstUser, SocialProvider.GOOGLE, "same-subject"));
        entityManager.flush();

        // When & Then
        assertThatThrownBy(() -> socialAccountRepository.save(
                SocialAccount.create(secondUser, SocialProvider.GOOGLE, "same-subject")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasRootCauseInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("한 회원에게 여러 소셜 계정을 연결할 수 없다")
    void save_duplicateUser_throwsException() {
        // Given
        User user = userRepository.save(createUser());
        socialAccountRepository.save(
                SocialAccount.create(user, SocialProvider.GOOGLE, "google-subject"));
        entityManager.flush();

        // When & Then
        assertThatThrownBy(() -> socialAccountRepository.save(
                SocialAccount.create(user, SocialProvider.KAKAO, "kakao-subject")))
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
