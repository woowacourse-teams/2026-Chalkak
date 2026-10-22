package com.chalkak.backend.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.UnauthorizedException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserPushPreferencesTest {

    @Test
    @DisplayName("새 회원은 주제·검수·AI 리뷰 푸시를 모두 수신하도록 설정된다")
    void create_newUser_enablesAllPushPreferences() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());

        // When & Then
        assertThat(user.isTopicPushEnabled()).isTrue();
        assertThat(user.isModerationPushEnabled()).isTrue();
        assertThat(user.isAiReviewPushEnabled()).isTrue();
    }

    @Test
    @DisplayName("주제·검수·AI 리뷰 푸시 수신 설정을 독립적으로 변경한다")
    void updatePushPreferences_activeUser_updatesEachPreference() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());

        // When
        user.updatePushPreferences(
                false,
                true,
                true
        );

        // Then
        assertThat(user.isTopicPushEnabled()).isFalse();
        assertThat(user.isModerationPushEnabled()).isTrue();
        assertThat(user.isAiReviewPushEnabled()).isTrue();

        // When
        user.updatePushPreferences(
                true,
                false,
                true
        );

        // Then
        assertThat(user.isTopicPushEnabled()).isTrue();
        assertThat(user.isModerationPushEnabled()).isFalse();
        assertThat(user.isAiReviewPushEnabled()).isTrue();

        // When
        user.updatePushPreferences(
                true,
                true,
                false
        );

        // Then
        assertThat(user.isTopicPushEnabled()).isTrue();
        assertThat(user.isModerationPushEnabled()).isTrue();
        assertThat(user.isAiReviewPushEnabled()).isFalse();
    }

    @Test
    @DisplayName("탈퇴 회원은 푸시 수신 설정을 변경할 수 없다")
    void updatePushPreferences_withdrawnUser_throwsUnauthorizedException() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());
        user.withdraw();

        // When & Then
        assertThatThrownBy(() -> user.updatePushPreferences(
                false,
                false,
                false
        ))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.");
    }
}
