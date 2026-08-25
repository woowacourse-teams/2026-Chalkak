package com.chalkak.backend.auth.api.support;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 이미지 처리 Lambda 콜백의 HMAC 인증기. 서명 대상 경로에 콜백 종류가 들어가므로 사인 콜백 서명을 포스트
 * 콜백에 재사용할 수 없다.
 *
 * <p>본문 해시는 <b>수신 원문</b>으로 계산해야 한다. 역직렬화 후 재직렬화한 문자열은 공백과 키 순서가 달라져
 * 서명이 어긋난다.
 */
@Component
public class ProcessingCallbackAuthenticator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SHA_256_ALGORITHM = "SHA-256";
    private static final String SIGNATURE_PREFIX = "v1=";
    private static final long MAX_CLOCK_SKEW_SECONDS = 300L;

    private final byte[] secret;

    public ProcessingCallbackAuthenticator(
            @Value("${IMAGE_PROCESSOR_CALLBACK_SECRET}") String secret
    ) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("IMAGE_PROCESSOR_CALLBACK_SECRET은 32자 이상이어야 합니다.");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public void authenticate(
            String path,
            String rawBody,
            String timestamp,
            String signature
    ) {
        long requestedAt = parseTimestamp(timestamp);
        if (Math.abs(Instant.now().getEpochSecond() - requestedAt) > MAX_CLOCK_SKEW_SECONDS) {
            throw unauthorized();
        }

        byte[] expected = sign(timestamp, path, rawBody);
        byte[] provided = decodeSignature(signature);
        if (!MessageDigest.isEqual(expected, provided)) {
            throw unauthorized();
        }
    }

    private long parseTimestamp(String timestamp) {
        try {
            return Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            throw unauthorized();
        }
    }

    private byte[] sign(String timestamp, String path, String rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            String payload = timestamp + "\nPOST\n" + path + "\n" + bodyHash(rawBody);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("이미지 처리 콜백 서명을 생성할 수 없습니다.", exception);
        }
    }

    private String bodyHash(String rawBody) throws NoSuchAlgorithmException {
        byte[] body = rawBody == null
                ? new byte[0]
                : rawBody.getBytes(StandardCharsets.UTF_8);

        return HexFormat.of().formatHex(
                MessageDigest.getInstance(SHA_256_ALGORITHM).digest(body)
        );
    }

    private byte[] decodeSignature(String signature) {
        if (signature == null || !signature.startsWith(SIGNATURE_PREFIX)) {
            throw unauthorized();
        }
        try {
            return HexFormat.of().parseHex(signature.substring(SIGNATURE_PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            throw unauthorized();
        }
    }

    private UnauthorizedException unauthorized() {
        return new UnauthorizedException(ErrorCode.UNAUTHORIZED, "유효하지 않은 이미지 처리 콜백입니다.");
    }
}
