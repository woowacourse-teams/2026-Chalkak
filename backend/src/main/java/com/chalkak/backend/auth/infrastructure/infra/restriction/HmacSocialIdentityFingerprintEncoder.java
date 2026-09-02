package com.chalkak.backend.auth.infrastructure.infra.restriction;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.service.SocialIdentityFingerprintEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HmacSocialIdentityFingerprintEncoder implements
        SocialIdentityFingerprintEncoder {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    public HmacSocialIdentityFingerprintEncoder(String secret) {
        this.secret = HexFormat.of().parseHex(secret);
    }

    @Override
    public String encode(SocialProvider provider, String subject) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            byte[] fingerprint = mac.doFinal(
                    (provider.name() + ":" + subject)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(fingerprint);
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException(
                    "소셜 계정 식별자를 변환할 수 없습니다.",
                    exception);
        }
    }
}
