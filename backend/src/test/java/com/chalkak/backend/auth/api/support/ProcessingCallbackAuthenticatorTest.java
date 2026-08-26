package com.chalkak.backend.auth.api.support;

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

class ProcessingCallbackAuthenticatorTest {

    private static final String SECRET = "test-callback-secret-with-enough-length";

    private final ProcessingCallbackAuthenticator authenticator =
            new ProcessingCallbackAuthenticator(SECRET);

    private static String signaturePath(UUID uploadId, String result) {
        return "/internal/v1/signature-processing/" + uploadId + "/" + result;
    }

    @Test
    @DisplayName("현재 시각의 올바른 HMAC 서명은 허용한다")
    void authenticate_validSignature_succeeds() {
        // Given
        UUID uploadId = UUID.randomUUID();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = sign(uploadId, "complete", timestamp);

        // When & Then
        assertThatCode(() -> authenticator.authenticate(signaturePath(uploadId, "complete"), null, timestamp, signature))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("잘못된 HMAC 서명은 거부한다")
    void authenticate_invalidSignature_throwsUnauthorizedException() {
        // Given
        UUID uploadId = UUID.randomUUID();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        // When & Then
        assertThatThrownBy(() -> authenticator.authenticate(signaturePath(uploadId, "complete"), null, timestamp, "invalid"))
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
        assertThatThrownBy(() -> authenticator.authenticate(signaturePath(uploadId, "complete"), null, timestamp, signature))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("timestamp가 없으면 인증 실패로 거부한다")
    void authenticate_nullTimestamp_throwsUnauthorizedException() {
        // Given
        UUID uploadId = UUID.randomUUID();

        // When & Then
        assertThatThrownBy(() -> authenticator.authenticate(signaturePath(uploadId, "complete"), null, null, "v1=00"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("서명이 없으면 인증 실패로 거부한다")
    void authenticate_nullSignature_throwsUnauthorizedException() {
        // Given
        UUID uploadId = UUID.randomUUID();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        // When & Then
        assertThatThrownBy(() -> authenticator.authenticate(signaturePath(uploadId, "complete"), null, timestamp, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("허용 시간 차 직전의 콜백 서명은 통과한다")
    void authenticate_justInsideClockSkew_succeeds() {
        // Given
        UUID uploadId = UUID.randomUUID();
        String timestamp = String.valueOf(Instant.now().minusSeconds(299).getEpochSecond());
        String signature = sign(uploadId, "complete", timestamp);

        // When & Then
        assertThatCode(() -> authenticator.authenticate(signaturePath(uploadId, "complete"), null, timestamp, signature))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("본문이 있는 콜백은 수신 원문의 해시로 검증한다")
    void authenticate_jsonBody_succeeds() {
        // Given
        UUID uploadId = UUID.randomUUID();
        String path = "/internal/v1/post-image-processing/" + uploadId + "/complete";
        String rawBody = "{\"width\":4032,\"height\":3024}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = sign(path, rawBody, timestamp);

        // When & Then
        assertThatCode(() -> authenticator.authenticate(
                path,
                rawBody.getBytes(StandardCharsets.UTF_8),
                timestamp,
                signature
        ))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("같은 값이어도 직렬화가 다른 본문의 서명은 거부한다")
    void authenticate_reserializedBody_throwsUnauthorizedException() {
        // Given
        UUID uploadId = UUID.randomUUID();
        String path = "/internal/v1/post-image-processing/" + uploadId + "/complete";
        String sentBody = "{\"width\":4032,\"height\":3024}";
        String reserializedBody = "{\"width\": 4032, \"height\": 3024}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = sign(path, sentBody, timestamp);

        // When & Then
        assertThatThrownBy(() ->
                authenticator.authenticate(
                        path,
                        reserializedBody.getBytes(StandardCharsets.UTF_8),
                        timestamp,
                        signature
                ))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("종류가 다른 경로로 만든 서명은 거부한다")
    void authenticate_otherKindPath_throwsUnauthorizedException() {
        // Given
        UUID uploadId = UUID.randomUUID();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = sign(signaturePath(uploadId, "complete"), null, timestamp);
        String postPath = "/internal/v1/post-image-processing/" + uploadId + "/complete";

        // When & Then
        assertThatThrownBy(() ->
                authenticator.authenticate(postPath, null, timestamp, signature))
                .isInstanceOf(UnauthorizedException.class);
    }

    private String sign(UUID uploadId, String result, String timestamp) {
        return sign(signaturePath(uploadId, result), null, timestamp);
    }

    private String sign(String path, String rawBody, String timestamp) {
        return signBytes(
                path,
                (rawBody == null) ? null : rawBody.getBytes(StandardCharsets.UTF_8),
                timestamp
        );
    }

    private String signBytes(String path, byte[] rawBody, String timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] body = (rawBody == null) ? new byte[0] : rawBody;
            String bodyHash = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(body));
            byte[] digest = mac.doFinal((timestamp + "\nPOST\n" + path + "\n" + bodyHash)
                    .getBytes(StandardCharsets.UTF_8));
            return "v1=" + HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    @DisplayName("유효한 UTF-8이 아닌 본문도 수신 바이트 그대로 검증한다")
    void authenticate_invalidUtf8Body_succeeds() {
        // Given
        String path = "/internal/v1/post-image-processing/" + UUID.randomUUID() + "/complete";
        byte[] rawBody = {(byte) 0xC3, 0x28};
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = signBytes(path, rawBody, timestamp);

        // When & Then
        assertThatCode(() -> authenticator.authenticate(path, rawBody, timestamp, signature))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("깨진 바이트가 서로 다르면 서명도 달라진다")
    void authenticate_differentInvalidUtf8Body_throwsUnauthorizedException() {
        // Given
        String path = "/internal/v1/post-image-processing/" + UUID.randomUUID() + "/complete";
        byte[] sentBody = {(byte) 0xC3, 0x28};
        byte[] tamperedBody = {(byte) 0xE0, 0x28};
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = signBytes(path, sentBody, timestamp);

        // When & Then
        assertThatThrownBy(() ->
                authenticator.authenticate(path, tamperedBody, timestamp, signature))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("허용 폭을 넘어 미래로 앞선 타임스탬프는 거부한다")
    void authenticate_farFutureTimestamp_throwsUnauthorizedException() {
        // Given
        UUID uploadId = UUID.randomUUID();
        String timestamp = String.valueOf(Instant.now().getEpochSecond() + 60);
        String signature = sign(uploadId, "complete", timestamp);

        // When & Then
        assertThatThrownBy(() -> authenticator.authenticate(
                signaturePath(uploadId, "complete"),
                null,
                timestamp,
                signature
        )).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("작은 시계 오차만큼 앞선 타임스탬프는 허용한다")
    void authenticate_slightlyFutureTimestamp_succeeds() {
        // Given
        UUID uploadId = UUID.randomUUID();
        String timestamp = String.valueOf(Instant.now().getEpochSecond() + 10);
        String signature = sign(uploadId, "complete", timestamp);

        // When & Then
        assertThatCode(() -> authenticator.authenticate(
                signaturePath(uploadId, "complete"),
                null,
                timestamp,
                signature
        )).doesNotThrowAnyException();
    }
}
