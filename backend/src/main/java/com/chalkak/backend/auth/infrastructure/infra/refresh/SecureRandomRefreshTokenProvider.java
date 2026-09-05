package com.chalkak.backend.auth.infrastructure.infra.refresh;

import com.chalkak.backend.auth.domain.GeneratedRefreshToken;
import com.chalkak.backend.auth.service.RefreshTokenGenerator;
import com.chalkak.backend.auth.service.RefreshTokenHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 리프레시 토큰을 불투명한 난수로 발급하고 저장용 해시로 변환한다. 토큰에 의미를 담지 않으므로
 * 서명 검증이 필요 없고, 서버는 해시만 들고 있어 저장소가 유출돼도 토큰을 복원할 수 없다.
 */
public class SecureRandomRefreshTokenProvider implements
        RefreshTokenGenerator,
        RefreshTokenHasher {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder base64Encoder = Base64.getUrlEncoder().withoutPadding();

    @Override
    public GeneratedRefreshToken generateToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        String value = base64Encoder.encodeToString(tokenBytes);
        return new GeneratedRefreshToken(value, encode(value));
    }

    /**
     * 토큰은 이미 충분한 엔트로피를 가진 난수라 사전 공격 대상이 아니므로, 비밀번호처럼 느린 해시를
     * 쓰지 않고 조회마다 부담 없는 SHA-256으로 고정 길이 hex를 만든다.
     */
    @Override
    public String encode(String refreshToken) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = messageDigest.digest(
                    refreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "리프레시 토큰을 변환할 수 없습니다.",
                    exception);
        }
    }
}
