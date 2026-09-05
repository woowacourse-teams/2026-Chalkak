package com.chalkak.backend.auth.infrastructure.infra.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AesGcmAppleAuthorizationCipherTest {

    private static final String ENCRYPTION_KEY =
            "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";
    private static final String OTHER_ENCRYPTION_KEY =
            "ffeeddccbbaa99887766554433221100ffeeddccbbaa99887766554433221100";
    private static final String REFRESH_TOKEN = "apple-refresh-token";

    @Test
    @DisplayName("Refresh Token을 암호화한 뒤 복호화하면 원본을 반환한다")
    void decrypt_encryptedRefreshToken_returnsOriginalRefreshToken() {
        // Given
        AesGcmAppleAuthorizationCipher cipher = createCipher(ENCRYPTION_KEY);
        String encrypted = cipher.encrypt(REFRESH_TOKEN);

        // When
        String decrypted = cipher.decrypt(encrypted);

        // Then
        assertThat(decrypted).isEqualTo(REFRESH_TOKEN);
    }

    @Test
    @DisplayName("같은 Refresh Token을 암호화해도 매번 다른 암호문을 생성한다")
    void encrypt_sameRefreshToken_generatesDifferentCiphertext() {
        // Given
        AesGcmAppleAuthorizationCipher cipher = createCipher(ENCRYPTION_KEY);

        // When
        String first = cipher.encrypt(REFRESH_TOKEN);
        String second = cipher.encrypt(REFRESH_TOKEN);

        // Then
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("다른 암호화 키로 Refresh Token을 복호화할 수 없다")
    void decrypt_withDifferentKey_throwsException() {
        // Given
        AesGcmAppleAuthorizationCipher encryptor = createCipher(ENCRYPTION_KEY);
        AesGcmAppleAuthorizationCipher decryptor = createCipher(OTHER_ENCRYPTION_KEY);
        String encrypted = encryptor.encrypt(REFRESH_TOKEN);

        // When & Then
        assertThatThrownBy(() -> decryptor.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("암호화된 Apple refresh token을 복호화할 수 없습니다.");
    }

    @Test
    @DisplayName("암호문이 변조되면 Refresh Token을 복호화할 수 없다")
    void decrypt_tamperedCiphertext_throwsException() {
        // Given
        AesGcmAppleAuthorizationCipher cipher = createCipher(ENCRYPTION_KEY);
        byte[] payload = Base64.getDecoder().decode(cipher.encrypt(REFRESH_TOKEN));
        payload[payload.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(payload);

        // When & Then
        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("암호화된 Apple refresh token을 복호화할 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("Refresh Token이 없으면 암호화할 수 없다")
    void encrypt_missingRefreshToken_throwsException(String refreshToken) {
        // Given
        AesGcmAppleAuthorizationCipher cipher = createCipher(ENCRYPTION_KEY);

        // When & Then
        assertThatThrownBy(() -> cipher.encrypt(refreshToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Apple refresh token이 필요합니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("암호화된 Refresh Token이 없으면 복호화할 수 없다")
    void decrypt_missingEncryptedRefreshToken_throwsException(String encryptedRefreshToken) {
        // Given
        AesGcmAppleAuthorizationCipher cipher = createCipher(ENCRYPTION_KEY);

        // When & Then
        assertThatThrownBy(() -> cipher.decrypt(encryptedRefreshToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("암호화된 Apple refresh token이 필요합니다.");
    }

    @Test
    @DisplayName("Base64 형식이 아닌 암호문은 복호화할 수 없다")
    void decrypt_invalidBase64_throwsException() {
        // Given
        AesGcmAppleAuthorizationCipher cipher = createCipher(ENCRYPTION_KEY);

        // When & Then
        assertThatThrownBy(() -> cipher.decrypt("invalid-base64"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("암호화된 Apple refresh token을 복호화할 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "00112233",
            "zz112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
    })
    @DisplayName("64자리 16진수가 아닌 암호화 키는 사용할 수 없다")
    void constructor_invalidEncryptionKey_throwsException(String encryptionKey) {
        // When & Then
        assertThatThrownBy(() -> createCipher(encryptionKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Apple refresh token 암호화 키는 64자리 16진수여야 합니다.");
    }

    private AesGcmAppleAuthorizationCipher createCipher(String encryptionKey) {
        AppleAuthorizationEncryptionProperties properties =
                new AppleAuthorizationEncryptionProperties(encryptionKey);
        return new AesGcmAppleAuthorizationCipher(properties);
    }
}
