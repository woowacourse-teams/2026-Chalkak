package com.chalkak.backend.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.UnauthorizedException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserPushPreferencesTest {

    @Test
    @DisplayName("새 회원은 주제와 검수 푸시를 모두 수신하도록 설정된다")
    void create_newUser_enablesBothPushPreferences() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());

        // When & Then
        assertThat(user.isTopicPushEnabled()).isTrue();
        assertThat(user.isModerationPushEnabled()).isTrue();
    }

    @Test
    @DisplayName("주제와 검수 푸시 수신 설정을 독립적으로 변경한다")
    void updatePushPreferences_activeUser_updatesBothPreferences() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());

        // When
        user.updatePushPreferences(false, true);

        // Then
        assertThat(user.isTopicPushEnabled()).isFalse();
        assertThat(user.isModerationPushEnabled()).isTrue();

        // When
        user.updatePushPreferences(true, false);

        // Then
        assertThat(user.isTopicPushEnabled()).isTrue();
        assertThat(user.isModerationPushEnabled()).isFalse();
    }

    @Test
    @DisplayName("탈퇴 회원은 푸시 수신 설정을 변경할 수 없다")
    void updatePushPreferences_withdrawnUser_throwsUnauthorizedException() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());
        user.withdraw();

        // When & Then
        assertThatThrownBy(() -> user.updatePushPreferences(false, false))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.");
    }
}
