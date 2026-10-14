package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class NotificationSettingsServiceTest extends IntegrationTestSupport {

    @Autowired
    private NotificationSettingsService notificationSettingsService;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @ParameterizedTest
    @CsvSource({"true, true", "true, false", "false, true", "false, false"})
    @DisplayName("회원에게 저장된 주제·검수 푸시 설정을 그대로 조회한다")
    void getSettings_savedPreferences_returnsStoredValues(boolean topic, boolean moderation) {
        // Given
        User user = userRepository.save(UserFixture.create());
        user.updatePushPreferences(topic, moderation);
        entityManager.flush();
        entityManager.clear();

        // When
        NotificationSettingsResult result = notificationSettingsService.getSettings(user.getId());

        // Then
        assertThat(result).isEqualTo(new NotificationSettingsResult(topic, moderation));
    }

    @Test
    @DisplayName("없는 회원의 푸시 설정은 인증 오류로 거부한다")
    void getSettings_missingUser_throwsUnauthorizedException() {
        // When & Then
        assertThatThrownBy(() -> notificationSettingsService.getSettings(UUID.randomUUID()))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("탈퇴한 회원의 푸시 설정은 인증 오류로 거부한다")
    void getSettings_withdrawnUser_throwsUnauthorizedException() {
        // Given
        User user = userRepository.save(UserFixture.create());
        user.withdraw();
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThatThrownBy(() -> notificationSettingsService.getSettings(user.getId()))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @ParameterizedTest
    @CsvSource({
            "true, true, false, , false, true",
            "true, true, , false, true, false",
            "false, false, true, , true, false",
            "false, false, , true, false, true",
            "true, true, false, false, false, false",
            "false, false, true, true, true, true"
    })
    @DisplayName("전달한 수신 설정만 저장하고 같은 요청을 반복해도 설정을 유지한다")
    void updateSettings_providedPreferences_persistsOnlyRequestedValues(
            boolean initialTopic,
            boolean initialModeration,
            Boolean topic,
            Boolean moderation,
            boolean expectedTopic,
            boolean expectedModeration
    ) {
        // Given
        User user = userRepository.save(UserFixture.create());
        user.updatePushPreferences(initialTopic, initialModeration);
        entityManager.flush();
        entityManager.clear();

        // When
        notificationSettingsService.updateSettings(user.getId(), topic, moderation);
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(notificationSettingsService.getSettings(user.getId()))
                .isEqualTo(new NotificationSettingsResult(expectedTopic, expectedModeration));

        // When
        notificationSettingsService.updateSettings(user.getId(), topic, moderation);
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(notificationSettingsService.getSettings(user.getId()))
                .isEqualTo(new NotificationSettingsResult(expectedTopic, expectedModeration));
    }

    @Test
    @DisplayName("없는 회원의 수신 설정 수정은 인증 오류로 거부한다")
    void updateSettings_missingUser_throwsUnauthorizedException() {
        // When & Then
        assertThatThrownBy(
                () -> notificationSettingsService.updateSettings(UUID.randomUUID(), false, null))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("탈퇴 회원의 수신 설정 수정은 거부하고 기존 값을 보존한다")
    void updateSettings_withdrawnUser_throwsUnauthorizedException() {
        // Given
        User user = userRepository.save(UserFixture.create());
        user.withdraw();
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThatThrownBy(
                () -> notificationSettingsService.updateSettings(user.getId(), false, null))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.UNAUTHORIZED));
        entityManager.flush();
        entityManager.clear();
        User saved = userRepository.findById(user.getId()).orElseThrow();
        assertThat(saved.isTopicPushEnabled()).isTrue();
        assertThat(saved.isModerationPushEnabled()).isTrue();
    }
}
