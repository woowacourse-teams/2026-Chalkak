package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationSettingsServiceTest {

    private static final UUID USER_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b657a01");

    @InjectMocks
    private NotificationSettingsService notificationSettingsService;

    @Mock
    private UserRepository userRepository;

    @ParameterizedTest
    @CsvSource({
            "true, true, true, true", "true, false, true, false",
            "false, true, true, false", "false, false, true, true",
            "true, true, false, false", "false, false, false, false"})
    @DisplayName("회원에게 저장된 주제·검수·AI 리뷰·좋아요 푸시 설정을 그대로 조회한다")
    void getSettings_savedPreferences_returnsStoredValues(
            boolean topic,
            boolean moderation,
            boolean aiReview,
            boolean like
    ) {
        // Given
        User user = UserFixture.create(USER_ID);
        user.updatePushPreferences(
                topic,
                moderation,
                aiReview,
                like
        );

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        // When
        NotificationSettingsResult result = notificationSettingsService.getSettings(user.getId());

        // Then
        assertThat(result).isEqualTo(new NotificationSettingsResult(
                topic,
                moderation,
                aiReview,
                like
        ));
    }

    @Test
    @DisplayName("없는 회원의 푸시 설정은 인증 오류로 거부한다")
    void getSettings_missingUser_throwsUnauthorizedException() {
        // Given
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> notificationSettingsService.getSettings(USER_ID))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("탈퇴한 회원의 푸시 설정은 인증 오류로 거부한다")
    void getSettings_withdrawnUser_throwsUnauthorizedException() {
        // Given
        User user = UserFixture.create(USER_ID);
        user.withdraw();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        // When & Then
        assertThatThrownBy(() -> notificationSettingsService.getSettings(user.getId()))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @ParameterizedTest
    @CsvSource({
            "true, true, true, true, false, , , , false, true, true, true",
            "true, true, true, true, , false, , , true, false, true, true",
            "true, true, true, true, , , false, , true, true, false, true",
            "true, true, true, true, , , , false, true, true, true, false",
            "false, false, false, false, true, , , , true, false, false, false",
            "false, false, false, false, , true, , , false, true, false, false",
            "false, false, false, false, , , true, , false, false, true, false",
            "false, false, false, false, , , , true, false, false, false, true",
            "true, true, true, true, false, false, false, false, false, false, false, false",
            "false, false, false, false, true, true, true, true, true, true, true, true"
    })
    @DisplayName("전달한 수신 설정만 바꾸고 같은 요청을 반복해도 설정을 유지한다")
    void updateSettings_providedPreferences_updatesOnlyRequestedValues(
            boolean initialTopic,
            boolean initialModeration,
            boolean initialAiReview,
            boolean initialLike,
            Boolean topic,
            Boolean moderation,
            Boolean aiReview,
            Boolean like,
            boolean expectedTopic,
            boolean expectedModeration,
            boolean expectedAiReview,
            boolean expectedLike
    ) {
        // Given
        User user = UserFixture.create(USER_ID);
        user.updatePushPreferences(
                initialTopic,
                initialModeration,
                initialAiReview,
                initialLike
        );

        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        // When
        notificationSettingsService.updateSettings(
                user.getId(),
                topic,
                moderation,
                aiReview,
                like
        );

        // Then
        assertThat(notificationSettingsService.getSettings(user.getId()))
                .isEqualTo(new NotificationSettingsResult(
                        expectedTopic,
                        expectedModeration,
                        expectedAiReview,
                        expectedLike
                ));

        // When
        notificationSettingsService.updateSettings(
                user.getId(),
                topic,
                moderation,
                aiReview,
                like
        );

        // Then
        assertThat(notificationSettingsService.getSettings(user.getId()))
                .isEqualTo(new NotificationSettingsResult(
                        expectedTopic,
                        expectedModeration,
                        expectedAiReview,
                        expectedLike
                ));
    }

    @Test
    @DisplayName("없는 회원의 수신 설정 수정은 인증 오류로 거부한다")
    void updateSettings_missingUser_throwsUnauthorizedException() {
        // Given
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(
                () -> notificationSettingsService.updateSettings(
                        USER_ID,
                        false,
                        null,
                        null,
                        null
                ))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("탈퇴 회원의 수신 설정 수정은 거부하고 기존 값을 보존한다")
    void updateSettings_withdrawnUser_throwsUnauthorizedException() {
        // Given
        User user = UserFixture.create(USER_ID);
        user.withdraw();
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));

        // When & Then
        assertThatThrownBy(
                () -> notificationSettingsService.updateSettings(
                        user.getId(),
                        false,
                        null,
                        false,
                        false
                ))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(user.isTopicPushEnabled()).isTrue();
        assertThat(user.isModerationPushEnabled()).isTrue();
        assertThat(user.isAiReviewPushEnabled()).isTrue();
        assertThat(user.isLikePushEnabled()).isTrue();
    }
}
