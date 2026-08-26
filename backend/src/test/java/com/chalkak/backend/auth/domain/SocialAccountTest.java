package com.chalkak.backend.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SocialAccountTest {

    @Test
    @DisplayName("소셜 제공자와 subject를 회원에게 연결한다")
    void create_validSocialIdentity_connectsUser() {
        // Given
        User user = User.create(
                null,
                new SignatureStorageKeys(
                        "chalkak/signatures/dev/original/signature.png",
                        "chalkak/signatures/dev/thumbnail/signature.png"));

        // When
        SocialAccount socialAccount = SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                "google-subject");

        // Then
        assertThat(socialAccount.getUser()).isSameAs(user);
        assertThat(socialAccount.getProvider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(socialAccount.getSubject()).isEqualTo("google-subject");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    @DisplayName("subject가 비어 있으면 소셜 계정을 생성할 수 없다")
    void create_blankSubject_throwsException(String subject) {
        // Given
        User user = User.create(
                null,
                new SignatureStorageKeys(
                        "chalkak/signatures/dev/original/signature.png",
                        "chalkak/signatures/dev/thumbnail/signature.png"));

        // When & Then
        assertThatThrownBy(() -> SocialAccount.create(user, SocialProvider.GOOGLE, subject))
                .isInstanceOf(BusinessException.class)
                .hasMessage("소셜 계정 식별 정보가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("subject가 255자를 초과하면 소셜 계정을 생성할 수 없다")
    void create_overlongSubject_throwsException() {
        // Given
        User user = User.create(
                null,
                new SignatureStorageKeys(
                        "chalkak/signatures/dev/original/signature.png",
                        "chalkak/signatures/dev/thumbnail/signature.png"));
        String subject = "a".repeat(256);

        // When & Then
        assertThatThrownBy(() -> SocialAccount.create(user, SocialProvider.GOOGLE, subject))
                .isInstanceOf(BusinessException.class)
                .hasMessage("소셜 계정 식별 정보가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("회원이나 제공자가 없으면 소셜 계정을 생성할 수 없다")
    void create_missingAssociation_throwsException() {
        // Given
        User user = User.create(
                null,
                new SignatureStorageKeys(
                        "chalkak/signatures/dev/original/signature.png",
                        "chalkak/signatures/dev/thumbnail/signature.png"));

        // When & Then
        assertThatThrownBy(() -> SocialAccount.create(null, SocialProvider.GOOGLE, "subject"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("소셜 계정 연결 정보가 올바르지 않습니다.");
        assertThatThrownBy(() -> SocialAccount.create(user, null, "subject"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("소셜 계정 연결 정보가 올바르지 않습니다.");
    }
}
