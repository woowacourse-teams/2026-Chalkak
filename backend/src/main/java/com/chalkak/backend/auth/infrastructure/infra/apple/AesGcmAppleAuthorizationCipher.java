package com.chalkak.backend.auth.infrastructure.infra.apple;

import com.chalkak.backend.auth.service.AppleAuthorizationCipher;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class AesGcmAppleAuthorizationCipher implements AppleAuthorizationCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int KEY_HEX_LENGTH = 64;
    private static final int IV_BYTE_LENGTH = 12;
    private static final int AUTHENTICATION_TAG_BIT_LENGTH = 128;
    private static final int AUTHENTICATION_TAG_BYTE_LENGTH =
            AUTHENTICATION_TAG_BIT_LENGTH / Byte.SIZE;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKey secretKey;

    public AesGcmAppleAuthorizationCipher(
            AppleAuthorizationEncryptionProperties properties
    ) {
        this.secretKey = createSecretKey(properties.key());
    }

    @Override
    public String encrypt(String refreshToken) {
        validateRefreshToken(refreshToken);
        byte[] iv = generateIv();

        try {
            Cipher cipher = createCipher(Cipher.ENCRYPT_MODE, iv);
            byte[] encrypted = cipher.doFinal(refreshToken.getBytes(StandardCharsets.UTF_8));
            return encode(iv, encrypted);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Apple refresh token을 암호화할 수 없습니다.", exception);
        }
    }

    @Override
    public String decrypt(String encryptedRefreshToken) {
        validateEncryptedRefreshToken(encryptedRefreshToken);

        try {
            byte[] payload = Base64.getDecoder().decode(encryptedRefreshToken);
            validatePayload(payload);
            byte[] iv = extractIv(payload);
            byte[] encrypted = extractEncrypted(payload);
            Cipher cipher = createCipher(Cipher.DECRYPT_MODE, iv);
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "암호화된 Apple refresh token을 복호화할 수 없습니다.",
                    exception);
        }
    }

    private SecretKey createSecretKey(String hexKey) {
        if (hexKey == null
                || hexKey.length() != KEY_HEX_LENGTH
                || !hexKey.matches("[0-9A-Fa-f]+")) {
            throw new IllegalArgumentException(
                    "Apple refresh token 암호화 키는 64자리 16진수여야 합니다.");
        }
        return new SecretKeySpec(HexFormat.of().parseHex(hexKey), KEY_ALGORITHM);
    }

    private void validateRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Apple refresh token이 필요합니다.");
        }
    }

    private void validateEncryptedRefreshToken(String encryptedRefreshToken) {
        if (encryptedRefreshToken == null || encryptedRefreshToken.isBlank()) {
            throw new IllegalArgumentException("암호화된 Apple refresh token이 필요합니다.");
        }
    }

    private byte[] generateIv() {
        byte[] iv = new byte[IV_BYTE_LENGTH];
        secureRandom.nextBytes(iv);
        return iv;
    }

    private Cipher createCipher(int mode, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec parameterSpec =
                new GCMParameterSpec(AUTHENTICATION_TAG_BIT_LENGTH, iv);
        cipher.init(mode, secretKey, parameterSpec);
        return cipher;
    }

    private String encode(byte[] iv, byte[] encrypted) {
        ByteBuffer payload = ByteBuffer.allocate(iv.length + encrypted.length);
        payload.put(iv);
        payload.put(encrypted);
        return Base64.getEncoder().encodeToString(payload.array());
    }

    private void validatePayload(byte[] payload) {
        int minimumPayloadLength = IV_BYTE_LENGTH + AUTHENTICATION_TAG_BYTE_LENGTH;
        if (payload.length <= minimumPayloadLength) {
            throw new IllegalArgumentException("암호화된 값의 길이가 올바르지 않습니다.");
        }
    }

    private byte[] extractIv(byte[] payload) {
        byte[] iv = new byte[IV_BYTE_LENGTH];
        System.arraycopy(payload, 0, iv, 0, IV_BYTE_LENGTH);
        return iv;
    }

    private byte[] extractEncrypted(byte[] payload) {
        int encryptedLength = payload.length - IV_BYTE_LENGTH;
        byte[] encrypted = new byte[encryptedLength];
        System.arraycopy(payload, IV_BYTE_LENGTH, encrypted, 0, encryptedLength);
        return encrypted;
    }
}
