package com.chalkak.backend.user.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.repository.UserRepository;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UserRepositoryImpl.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

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

    @Test
    @DisplayName("탈퇴한 회원은 활성 조회에서 제외하지만 원본 조회에는 남는다")
    void findActiveById_withdrawnUser_returnsEmpty() {
        // Given
        User user = userRepository.save(createUser("calendar-author@example.com"));
        assertThat(userRepository.findActiveById(user.getId())).isPresent();
        user.withdraw();
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThat(userRepository.findActiveById(user.getId())).isEmpty();
        assertThat(userRepository.findById(user.getId())).isPresent();
    }

    @ParameterizedTest
    @CsvSource({
            "true, true, true", "true, false, true", "false, true, true", "false, false, true",
            "true, true, false", "false, false, false"})
    @DisplayName("주제, 검수, AI 리뷰 수신 설정의 각 필드를 DB에 저장하고 다시 읽는다")
    void findById_savedPushPreferences_returnsStoredValues(
            boolean topic,
            boolean moderation,
            boolean aiReview
    ) {
        // Given
        User user = userRepository.save(createUser("preferences@example.com"));
        user.updatePushPreferences(
                topic,
                moderation,
                aiReview
        );
        entityManager.flush();
        entityManager.clear();

        // When
        User found = userRepository.findById(user.getId()).orElseThrow();

        // Then
        assertThat(found.isTopicPushEnabled()).isEqualTo(topic);
        assertThat(found.isModerationPushEnabled()).isEqualTo(moderation);
        assertThat(found.isAiReviewPushEnabled()).isEqualTo(aiReview);
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
