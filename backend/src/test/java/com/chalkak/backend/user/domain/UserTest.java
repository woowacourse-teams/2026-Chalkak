package com.chalkak.backend.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    @DisplayName("탈퇴하면 개인 식별 정보가 비식별화된다")
    void withdraw_activeUser_anonymizesPersonalData() {
        // Given
        UUID id = UUID.randomUUID();
        User user = UserFixture.create(id);

        // When
        user.withdraw();

        // Then
        assertThat(user.getEmail()).isEqualTo("withdrawn+" + id + "@chalkak.invalid");
        assertThat(user.getSignatureOriginalStorageKey()).isEqualTo("withdrawn/" + id);
        assertThat(user.getSignatureThumbnailStorageKey()).isNull();
    }

    @Test
    @DisplayName("탈퇴하면 탈퇴 시각이 기록된다")
    void withdraw_activeUser_recordsDeletedAt() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());

        // When
        user.withdraw();

        // Then
        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("이미 탈퇴한 회원은 다시 탈퇴할 수 없다")
    void withdraw_alreadyWithdrawnUser_throwsException() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());
        user.withdraw();

        // When & Then
        assertThatThrownBy(user::withdraw)
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 탈퇴한 회원입니다.");
    }

    @Test
    @DisplayName("탈퇴 전에는 삭제된 회원이 아니다")
    void isDeleted_activeUser_returnsFalse() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());

        // When & Then
        assertThat(user.isDeleted()).isFalse();
    }
}
