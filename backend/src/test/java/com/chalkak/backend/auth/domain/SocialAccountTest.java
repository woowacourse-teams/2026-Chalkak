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

    private static final String SUBJECT = "google-subject";
    private static final String SUBJECT_HMAC =
            "921c5d35312df654eaa8ec114fd1de5a156cbcc64b23ddb6a709a9423f90c218";
    private static final String OTHER_SUBJECT_HMAC =
            "121c5d35312df654eaa8ec114fd1de5a156cbcc64b23ddb6a709a9423f90c218";

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
                SUBJECT,
                SUBJECT_HMAC);

        // Then
        assertThat(socialAccount.getUser()).isSameAs(user);
        assertThat(socialAccount.getProvider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(socialAccount.getSubject()).isEqualTo(SUBJECT);
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

    @Test
    @DisplayName("subject HMAC이 없으면 백필한다")
    void backfillSubjectHmac_missingHmac_setsHmac() {
        // Given
        SocialAccount socialAccount = new SocialAccount();

        // When
        socialAccount.backfillSubjectHmac(SUBJECT_HMAC);

        // Then
        assertThat(socialAccount.getSubjectHmac()).isEqualTo(SUBJECT_HMAC);
    }

    @Test
    @DisplayName("subject HMAC이 이미 있으면 백필로 덮어쓰지 않는다")
    void backfillSubjectHmac_existingHmac_preservesHmac() {
        // Given
        SocialAccount socialAccount = SocialAccount.create(
                User.create(
                        null,
                        new SignatureStorageKeys(
                                "chalkak/signatures/dev/original/signature.png",
                                "chalkak/signatures/dev/thumbnail/signature.png")),
                SocialProvider.GOOGLE,
                SUBJECT,
                SUBJECT_HMAC);

        // When
        socialAccount.backfillSubjectHmac(OTHER_SUBJECT_HMAC);

        // Then
        assertThat(socialAccount.getSubjectHmac()).isEqualTo(SUBJECT_HMAC);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "abc123"})
    @DisplayName("백필할 subject HMAC 형식이 올바르지 않으면 예외가 발생한다")
    void backfillSubjectHmac_invalidHmac_throwsException(String subjectHmac) {
        // Given
        SocialAccount socialAccount = new SocialAccount();

        // When & Then
        assertThatThrownBy(() -> socialAccount.backfillSubjectHmac(subjectHmac))
                .isInstanceOf(BusinessException.class)
                .hasMessage("소셜 계정 식별 정보가 올바르지 않습니다.");
    }
}
