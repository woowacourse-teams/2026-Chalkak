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
}
