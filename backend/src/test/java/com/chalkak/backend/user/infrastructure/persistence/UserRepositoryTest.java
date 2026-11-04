package com.chalkak.backend.user.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.support.PostgresRepositoryTest;
import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

@PostgresRepositoryTest
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
            "true, true, true, true", "true, false, true, false",
            "false, true, true, false", "false, false, true, true",
            "true, true, false, false", "false, false, false, false"})
    @DisplayName("주제·검수·AI 리뷰·좋아요 수신 설정의 각 필드를 DB에 저장하고 다시 읽는다")
    void findById_savedPushPreferences_returnsStoredValues(
            boolean topic,
            boolean moderation,
            boolean aiReview,
            boolean like
    ) {
        // Given
        User user = userRepository.save(createUser("preferences@example.com"));
        user.updatePushPreferences(
                topic,
                moderation,
                aiReview,
                like
        );
        entityManager.flush();
        entityManager.clear();

        // When
        User found = userRepository.findById(user.getId()).orElseThrow();

        // Then
        assertThat(found.isTopicPushEnabled()).isEqualTo(topic);
        assertThat(found.isModerationPushEnabled()).isEqualTo(moderation);
        assertThat(found.isAiReviewPushEnabled()).isEqualTo(aiReview);
        assertThat(found.isLikePushEnabled()).isEqualTo(like);
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
