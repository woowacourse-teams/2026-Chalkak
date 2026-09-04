package com.chalkak.backend.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AppleAuthorizationTest {

    private static final String SUBJECT_HMAC =
            "921c5d35312df654eaa8ec114fd1de5a156cbcc64b23ddb6a709a9423f90c218";
    private static final String CLIENT_ID = "com.chalkak.ios";
    private static final String ENCRYPTED_REFRESH_TOKEN = "encrypted-refresh-token";

    @Test
    @DisplayName("Apple 소셜 계정에 인증 정보를 연결한다")
    void create_validAuthorization_connectsAppleAccount() {
        // Given
        SocialAccount socialAccount = createSocialAccount(SocialProvider.APPLE);

        // When
        AppleAuthorization authorization = AppleAuthorization.create(
                socialAccount,
                CLIENT_ID,
                ENCRYPTED_REFRESH_TOKEN);

        // Then
        assertThat(authorization.getSocialAccount()).isSameAs(socialAccount);
        assertThat(authorization.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(authorization.getEncryptedRefreshToken())
                .isEqualTo(ENCRYPTED_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("Apple이 아닌 소셜 계정에는 Apple 인증 정보를 연결할 수 없다")
    void create_nonAppleAccount_throwsException() {
        // Given
        SocialAccount socialAccount = createSocialAccount(SocialProvider.GOOGLE);

        // When & Then
        assertThatThrownBy(() -> AppleAuthorization.create(
                socialAccount,
                CLIENT_ID,
                ENCRYPTED_REFRESH_TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Apple 인증 정보가 올바르지 않습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("Client ID가 없으면 Apple 인증 정보를 생성할 수 없다")
    void create_missingClientId_throwsException(String clientId) {
        // Given
        SocialAccount socialAccount = createSocialAccount(SocialProvider.APPLE);

        // When & Then
        assertThatThrownBy(() -> AppleAuthorization.create(
                socialAccount,
                clientId,
                ENCRYPTED_REFRESH_TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Apple 인증 정보가 올바르지 않습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("암호화된 RT가 없으면 Apple 인증 정보를 생성할 수 없다")
    void create_missingEncryptedRefreshToken_throwsException(
            String encryptedRefreshToken
    ) {
        // Given
        SocialAccount socialAccount = createSocialAccount(SocialProvider.APPLE);

        // When & Then
        assertThatThrownBy(() -> AppleAuthorization.create(
                socialAccount,
                CLIENT_ID,
                encryptedRefreshToken))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Apple 인증 정보가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("Client ID나 암호화된 RT가 저장 길이를 넘으면 사용할 수 없다")
    void create_tooLongValue_throwsException() {
        // Given
        SocialAccount socialAccount = createSocialAccount(SocialProvider.APPLE);

        // When & Then
        assertThatThrownBy(() -> AppleAuthorization.create(
                socialAccount,
                "a".repeat(256),
                ENCRYPTED_REFRESH_TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Apple 인증 정보가 올바르지 않습니다.");
        assertThatThrownBy(() -> AppleAuthorization.create(
                socialAccount,
                CLIENT_ID,
                "a".repeat(4097)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Apple 인증 정보가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("저장 가능한 최대 길이의 Client ID와 암호화된 RT를 허용한다")
    void create_maximumLengthValue_createsAuthorization() {
        // When
        AppleAuthorization authorization = AppleAuthorization.create(
                createSocialAccount(SocialProvider.APPLE),
                "a".repeat(255),
                "a".repeat(4096));

        // Then
        assertThat(authorization.getClientId()).hasSize(255);
        assertThat(authorization.getEncryptedRefreshToken()).hasSize(4096);
    }

    private SocialAccount createSocialAccount(SocialProvider provider) {
        User user = User.create(
                null,
                new SignatureStorageKeys(
                        "chalkak/signatures/dev/original/signature.png",
                        "chalkak/signatures/dev/thumbnail/signature.png"));
        return SocialAccount.create(user, provider, SUBJECT_HMAC);
    }
}
