package com.chalkak.backend.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class AppleAuthorizationFingerprintEncoder {

    private static final String HASH_ALGORITHM = "SHA-256";

    public String encode(String encryptedRefreshToken) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] fingerprint = messageDigest.digest(
                    encryptedRefreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(fingerprint);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Apple 인증 정보의 지문을 생성할 수 없습니다.",
                    exception);
        }
    }
}
