package com.chalkak.backend.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AppleSignupAuthorizationTest {

    private static final String CLIENT_ID = "com.chalkak.ios";
    private static final String ENCRYPTED_REFRESH_TOKEN = "encrypted-refresh-token";

    @Test
    @DisplayName("Apple 회원가입용 Client ID와 암호화된 RT를 생성한다")
    void constructor_validValues_createsAuthorization() {
        // When
        AppleSignupAuthorization authorization = new AppleSignupAuthorization(
                CLIENT_ID,
                ENCRYPTED_REFRESH_TOKEN);

        // Then
        assertThat(authorization.clientId()).isEqualTo(CLIENT_ID);
        assertThat(authorization.encryptedRefreshToken())
                .isEqualTo(ENCRYPTED_REFRESH_TOKEN);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("Client ID가 없으면 Apple 회원가입 인증 정보를 생성할 수 없다")
    void constructor_missingClientId_throwsException(String clientId) {
        // When & Then
        assertThatThrownBy(() -> new AppleSignupAuthorization(
                clientId,
                ENCRYPTED_REFRESH_TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Apple 회원가입 인증 정보가 올바르지 않습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("암호화된 RT가 없으면 Apple 회원가입 인증 정보를 생성할 수 없다")
    void constructor_missingEncryptedRefreshToken_throwsException(
            String encryptedRefreshToken
    ) {
        // When & Then
        assertThatThrownBy(() -> new AppleSignupAuthorization(
                CLIENT_ID,
                encryptedRefreshToken))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Apple 회원가입 인증 정보가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("저장 가능한 최대 길이의 Client ID와 암호화된 RT를 허용한다")
    void constructor_maximumLengthValues_createsAuthorization() {
        // When
        AppleSignupAuthorization authorization = new AppleSignupAuthorization(
                "a".repeat(255),
                "a".repeat(4096));

        // Then
        assertThat(authorization.clientId()).hasSize(255);
        assertThat(authorization.encryptedRefreshToken()).hasSize(4096);
    }

    @Test
    @DisplayName("저장 길이를 넘는 Client ID나 암호화된 RT는 사용할 수 없다")
    void constructor_tooLongValues_throwsException() {
        // When & Then
        assertThatThrownBy(() -> new AppleSignupAuthorization(
                "a".repeat(256),
                ENCRYPTED_REFRESH_TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Apple 회원가입 인증 정보가 올바르지 않습니다.");
        assertThatThrownBy(() -> new AppleSignupAuthorization(
                CLIENT_ID,
                "a".repeat(4097)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Apple 회원가입 인증 정보가 올바르지 않습니다.");
    }
}
