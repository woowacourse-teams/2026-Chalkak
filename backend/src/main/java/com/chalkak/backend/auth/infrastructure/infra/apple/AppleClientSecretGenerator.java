package com.chalkak.backend.auth.infrastructure.infra.apple;

import com.chalkak.backend.auth.infrastructure.infra.oidc.apple.AppleOidcProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

public class AppleClientSecretGenerator {

    private static final String APPLE_AUDIENCE = "https://appleid.apple.com";
    private static final String PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PRIVATE_KEY_END = "-----END PRIVATE KEY-----";
    private static final Duration EXPIRATION = Duration.ofMinutes(5);

    private final AppleTokenProperties tokenProperties;
    private final AppleOidcProperties oidcProperties;
    private final Clock clock;
    private final ECPrivateKey privateKey;

    /**
     * 개인키를 생성자에서 즉시 파싱해, 설정이 잘못됐을 때 첫 로그인 요청이 아니라 애플리케이션
     * 기동 시점에 실패하게 한다.
     */
    public AppleClientSecretGenerator(
            AppleTokenProperties tokenProperties,
            AppleOidcProperties oidcProperties,
            Clock clock
    ) {
        this.tokenProperties = tokenProperties;
        this.oidcProperties = oidcProperties;
        this.clock = clock;
        this.privateKey = parsePrivateKey();
    }

    public String generate() {
        Instant issuedAt = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(tokenProperties.teamId())
                .subject(oidcProperties.clientId())
                .audience(APPLE_AUDIENCE)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plus(EXPIRATION)))
                .build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(tokenProperties.keyId())
                .build();
        SignedJWT clientSecret = new SignedJWT(header, claims);

        try {
            clientSecret.sign(new ECDSASigner(privateKey));
            return clientSecret.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException(
                    "Apple client_secret을 생성할 수 없습니다.",
                    exception);
        }
    }

    private ECPrivateKey parsePrivateKey() {
        try {
            byte[] pemBytes = Base64.getDecoder().decode(tokenProperties.privateKeyBase64());
            String pem = new String(pemBytes, StandardCharsets.US_ASCII);
            String encodedKey = extractEncodedKey(pem);
            byte[] keyBytes = Base64.getMimeDecoder().decode(encodedKey);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            Object parsedKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            if (parsedKey instanceof ECPrivateKey ecPrivateKey) {
                return ecPrivateKey;
            }
            throw invalidPrivateKey(null);
        } catch (IllegalArgumentException
                | NoSuchAlgorithmException
                | InvalidKeySpecException exception) {
            throw invalidPrivateKey(exception);
        }
    }

    private String extractEncodedKey(String pem) {
        if (!pem.contains(PRIVATE_KEY_BEGIN) || !pem.contains(PRIVATE_KEY_END)) {
            throw invalidPrivateKey(null);
        }
        return pem.replace(PRIVATE_KEY_BEGIN, "")
                .replace(PRIVATE_KEY_END, "")
                .replaceAll("\\s", "");
    }

    private IllegalStateException invalidPrivateKey(Exception cause) {
        return new IllegalStateException(
                "Apple 개인키 설정이 올바르지 않습니다.",
                cause);
    }
}
