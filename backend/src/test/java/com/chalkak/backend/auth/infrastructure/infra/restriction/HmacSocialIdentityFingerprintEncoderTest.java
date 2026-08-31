package com.chalkak.backend.auth.infrastructure.infra.restriction;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.auth.domain.SocialProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HmacSocialIdentityFingerprintEncoderTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("소셜 제공자와 subject를 HMAC-SHA256 지문으로 변환한다")
    void encode_providerAndSubject_returnsHmacSha256Fingerprint() {
        // Given
        HmacSocialIdentityFingerprintEncoder encoder =
                new HmacSocialIdentityFingerprintEncoder(SECRET);

        // When
        String fingerprint = encoder.encode(
                SocialProvider.GOOGLE,
                "google-subject");

        // Then
        assertThat(fingerprint)
                .isEqualTo("921c5d35312df654eaa8ec114fd1de5a156cbcc64b23ddb6a709a9423f90c218");
    }
}
