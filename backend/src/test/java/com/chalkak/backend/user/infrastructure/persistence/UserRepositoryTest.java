package com.chalkak.backend.user.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class UserRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("동일한 이메일을 가진 여러 회원을 저장할 수 있다")
    void save_duplicateEmail_succeeds() {
        // Given
        User firstUser = createUser("same@chalkak.test");
        User secondUser = createUser("same@chalkak.test");

        // When
        User savedFirstUser = userRepository.save(firstUser);
        User savedSecondUser = userRepository.save(secondUser);

        // Then
        assertThat(savedFirstUser.getId()).isNotEqualTo(savedSecondUser.getId());
        assertThat(savedFirstUser.getEmail()).isEqualTo(savedSecondUser.getEmail());
    }

    private User createUser(String email) {
        String unique = UUID.randomUUID().toString();
        return User.create(
                email,
                new SignatureStorageKeys(
                        "signatures/original/" + unique,
                        "signatures/thumbnail/" + unique));
    }
}
