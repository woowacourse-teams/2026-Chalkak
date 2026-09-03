package com.chalkak.backend.auth.infrastructure.infra.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.auth.infrastructure.infra.oidc.apple.AppleOidcProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AppleClientSecretGeneratorTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");

    @Test
    @DisplayName("Apple 규격의 ES256 client_secret을 생성한다")
    void generate_validPrivateKey_returnsSignedClientSecret() throws Exception {
        // Given
        KeyPair keyPair = generateEcKeyPair();
        AppleClientSecretGenerator generator = createGenerator(encodePrivateKey(keyPair.getPrivate()));

        // When
        String clientSecret = generator.generate();

        // Then
        SignedJWT jwt = SignedJWT.parse(clientSecret);
        assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic())))
                .isTrue();
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo("APPLEKEY01");
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("APPLETEAM1");
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("com.chalkak.ios");
        assertThat(jwt.getJWTClaimsSet().getAudience())
                .isEqualTo(List.of("https://appleid.apple.com"));
        assertThat(jwt.getJWTClaimsSet().getIssueTime().toInstant()).isEqualTo(NOW);
        assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant())
                .isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("개인키를 실제로 사용하기 전까지는 잘못된 더미값이어도 생성기를 만들 수 있다")
    void create_invalidPrivateKey_doesNotParseKeyEagerly() {
        // When & Then
        assertThatCode(() -> createGenerator("not-base64"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("개인키가 Base64 형식이 아니면 client_secret 생성을 거부한다")
    void generate_invalidBase64_throwsIllegalStateException() {
        // Given
        AppleClientSecretGenerator generator = createGenerator("not-base64");

        // When & Then
        assertThatThrownBy(generator::generate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Apple 개인키 설정이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("개인키에 PKCS8 PEM 형식이 없으면 client_secret 생성을 거부한다")
    void generate_invalidPem_throwsIllegalStateException() {
        // Given
        String invalidPem = Base64.getEncoder().encodeToString(
                "not-pem".getBytes(StandardCharsets.US_ASCII));
        AppleClientSecretGenerator generator = createGenerator(invalidPem);

        // When & Then
        assertThatThrownBy(generator::generate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Apple 개인키 설정이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("EC 개인키가 아니면 Apple client_secret 생성을 거부한다")
    void generate_nonEcPrivateKey_throwsIllegalStateException() throws Exception {
        // Given
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        PrivateKey privateKey = keyPairGenerator.generateKeyPair().getPrivate();
        AppleClientSecretGenerator generator = createGenerator(encodePrivateKey(privateKey));

        // When & Then
        assertThatThrownBy(generator::generate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Apple 개인키 설정이 올바르지 않습니다.");
    }

    private AppleClientSecretGenerator createGenerator(String privateKeyBase64) {
        AppleTokenProperties tokenProperties = new AppleTokenProperties(
                "APPLETEAM1",
                "APPLEKEY01",
                privateKeyBase64,
                "https://appleid.apple.com/auth/token",
                "https://appleid.apple.com/auth/revoke");
        AppleOidcProperties oidcProperties = new AppleOidcProperties(
                "https://appleid.apple.com",
                "https://appleid.apple.com/auth/keys",
                "com.chalkak.ios");
        return new AppleClientSecretGenerator(
                tokenProperties,
                oidcProperties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private KeyPair generateEcKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        return keyPairGenerator.generateKeyPair();
    }

    private String encodePrivateKey(PrivateKey privateKey) {
        String encodedKey = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(privateKey.getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + encodedKey
                + "\n-----END PRIVATE KEY-----\n";
        return Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.US_ASCII));
    }
}
