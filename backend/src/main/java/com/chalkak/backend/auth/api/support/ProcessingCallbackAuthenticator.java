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
 * <p>본문 해시는 <b>수신 바이트</b>로 계산해야 한다. 역직렬화 후 재직렬화한 문자열은 공백과 키 순서가 달라져
 * 서명이 어긋나고, 문자열로 디코딩했다 다시 인코딩한 바이트도 원문과 같다는 보장이 없다.
 */
@Component
public class ProcessingCallbackAuthenticator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SHA_256_ALGORITHM = "SHA-256";
    private static final String SIGNATURE_PREFIX = "v1=";
    private static final long MAX_AGE_SECONDS = 300L;
    // 미래 타임스탬프는 시계 오차로만 설명되므로 과거만큼 넉넉히 받을 이유가 없다. 양방향을 같은 폭으로
    // 열어 두면 캡처한 서명의 유효창이 두 배로 넓어진다.
    private static final long MAX_FUTURE_SKEW_SECONDS = 30L;

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
            byte[] rawBody,
            String timestamp,
            String signature
    ) {
        long requestedAt = parseTimestamp(timestamp);
        long age = Instant.now().getEpochSecond() - requestedAt;
        if (age > MAX_AGE_SECONDS || age < -MAX_FUTURE_SKEW_SECONDS) {
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

    private byte[] sign(String timestamp, String path, byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            String payload = timestamp + "\nPOST\n" + path + "\n" + bodyHash(rawBody);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("이미지 처리 콜백 서명을 생성할 수 없습니다.", exception);
        }
    }

    private String bodyHash(byte[] rawBody) throws NoSuchAlgorithmException {
        byte[] body = (rawBody == null) ? new byte[0] : rawBody;

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
