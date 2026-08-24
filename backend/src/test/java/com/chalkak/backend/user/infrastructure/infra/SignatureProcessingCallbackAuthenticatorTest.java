package com.chalkak.backend.user.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignatureProcessingCallbackAuthenticatorTest {

    private static final String SECRET = "test-callback-secret-with-enough-length";

    private final SignatureProcessingCallbackAuthenticator authenticator =
            new SignatureProcessingCallbackAuthenticator(SECRET);

    @Test
    @DisplayName("현재 시각의 올바른 HMAC 서명은 허용한다")
    void authenticate_validSignature_succeeds() {
        // Given
        UUID uploadId = UUID.randomUUID();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = sign(uploadId, "complete", timestamp);

        // When & Then
        assertThatCode(() -> authenticator.authenticate(uploadId, "complete", timestamp, signature))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("잘못된 HMAC 서명은 거부한다")
    void authenticate_invalidSignature_throwsUnauthorizedException() {
        // Given
        UUID uploadId = UUID.randomUUID();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        // When & Then
        assertThatThrownBy(() -> authenticator.authenticate(uploadId, "complete", timestamp, "invalid"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("5분이 지난 콜백 서명은 재사용 공격으로 보고 거부한다")
    void authenticate_expiredTimestamp_throwsUnauthorizedException() {
        // Given
        UUID uploadId = UUID.randomUUID();
        String timestamp = String.valueOf(Instant.now().minusSeconds(301).getEpochSecond());
        String signature = sign(uploadId, "complete", timestamp);

        // When & Then
        assertThatThrownBy(() -> authenticator.authenticate(uploadId, "complete", timestamp, signature))
                .isInstanceOf(UnauthorizedException.class);
    }

    private String sign(UUID uploadId, String result, String timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String path = "/internal/v1/signature-processing/" + uploadId + "/" + result;
            String bodyHash = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(new byte[0]));
            byte[] digest = mac.doFinal((timestamp + "\nPOST\n" + path + "\n" + bodyHash)
                    .getBytes(StandardCharsets.UTF_8));
            return "v1=" + HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
