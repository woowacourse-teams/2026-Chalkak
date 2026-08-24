package com.chalkak.backend.user.infrastructure.infra;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SignatureProcessingCallbackAuthenticator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "v1=";
    private static final String EMPTY_BODY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final long MAX_CLOCK_SKEW_SECONDS = 300L;

    private final byte[] secret;

    public SignatureProcessingCallbackAuthenticator(
            @Value("${IMAGE_PROCESSOR_CALLBACK_SECRET}") String secret
    ) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("IMAGE_PROCESSOR_CALLBACK_SECRET은 32자 이상이어야 합니다.");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public void authenticate(
            UUID uploadId,
            String result,
            String timestamp,
            String signature
    ) {
        long requestedAt = parseTimestamp(timestamp);
        if (Math.abs(Instant.now().getEpochSecond() - requestedAt) > MAX_CLOCK_SKEW_SECONDS) {
            throw unauthorized();
        }

        byte[] expected = sign(timestamp, callbackPath(uploadId, result));
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

    private byte[] sign(String timestamp, String path) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            String payload = timestamp + "\nPOST\n" + path + "\n" + EMPTY_BODY_SHA256;
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("이미지 처리 콜백 서명을 생성할 수 없습니다.", exception);
        }
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

    private String callbackPath(UUID uploadId, String result) {
        return "/internal/v1/signature-processing/" + uploadId + "/" + result;
    }

    private UnauthorizedException unauthorized() {
        return new UnauthorizedException(ErrorCode.UNAUTHORIZED, "유효하지 않은 이미지 처리 콜백입니다.");
    }
}
