package com.chalkak.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AppleAuthorizationFingerprintEncoderTest {

    @Test
    @DisplayName("Apple 인증 정보 암호문을 SHA-256 지문으로 변환한다")
    void encode_encryptedRefreshToken_returnsSha256Fingerprint() {
        // Given
        AppleAuthorizationFingerprintEncoder encoder =
                new AppleAuthorizationFingerprintEncoder();

        // When
        String fingerprint = encoder.encode("encrypted-refresh-token");

        // Then
        assertThat(fingerprint)
                .isEqualTo("fa52a3a1ce506d39fc04ecb7ea2fde99"
                        + "ba09f5839ba1062e9f6c2b2aaafb1d9d");
    }
}
