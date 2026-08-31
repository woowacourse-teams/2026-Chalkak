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

    private static final String SUBJECT_HMAC =
            "921c5d35312df654eaa8ec114fd1de5a156cbcc64b23ddb6a709a9423f90c218";

    @Test
    @DisplayName("소셜 제공자와 subject HMAC을 회원에게 연결한다")
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
                SUBJECT_HMAC);

        // Then
        assertThat(socialAccount.getUser()).isSameAs(user);
        assertThat(socialAccount.getProvider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(socialAccount.getSubjectHmac()).isEqualTo(SUBJECT_HMAC);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            " ",
            "ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890",
            "abc123",
            "g21c5d35312df654eaa8ec114fd1de5a156cbcc64b23ddb6a709a9423f90c218"
    })
    @DisplayName("subject HMAC 형식이 올바르지 않으면 소셜 계정을 생성할 수 없다")
    void create_invalidSubjectHmac_throwsException(String subjectHmac) {
        // Given
        User user = User.create(
                null,
                new SignatureStorageKeys(
                        "chalkak/signatures/dev/original/signature.png",
                        "chalkak/signatures/dev/thumbnail/signature.png"));

        // When & Then
        assertThatThrownBy(() -> SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                subjectHmac))
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
        assertThatThrownBy(() -> SocialAccount.create(null, SocialProvider.GOOGLE, SUBJECT_HMAC))
                .isInstanceOf(BusinessException.class)
                .hasMessage("소셜 계정 연결 정보가 올바르지 않습니다.");
        assertThatThrownBy(() -> SocialAccount.create(user, null, SUBJECT_HMAC))
                .isInstanceOf(BusinessException.class)
                .hasMessage("소셜 계정 연결 정보가 올바르지 않습니다.");
    }
}
