package com.chalkak.backend.auth.infrastructure.infra.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.CachingJWKSetSource;
import com.nimbusds.jose.jwk.source.JWKSetBasedJWKSource;
import com.nimbusds.jose.jwk.source.JWKSetSource;
import com.nimbusds.jose.jwk.source.RateLimitedJWKSetSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.Resource;
import com.nimbusds.jose.util.ResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class OidcIdTokenDecoderFactoryTest {

    private static final String PROVIDER_NAME = "Google";
    private static final String ISSUER = "https://accounts.google.com";
    private static final String AUDIENCE = "backend-client-id";
    private static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String KEY_ID = "signing-key";

    private RSAKey signingKey;
    private CountingResourceRetriever resourceRetriever;
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() throws JOSEException {
        signingKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        resourceRetriever = new CountingResourceRetriever(
                new JWKSet(signingKey.toPublicJWK()).toString());
        jwtDecoder = OidcIdTokenDecoderFactory.createDecoder(
                PROVIDER_NAME,
                ISSUER,
                AUDIENCE,
                OidcIdTokenDecoderFactory.createJwkSource(JWK_SET_URI, resourceRetriever));
    }

    @Test
    @DisplayName("공개키로 서명되고 발급자·aud·시각이 올바른 ID Token을 디코딩한다")
    void createDecoder_validIdToken_decodes() throws JOSEException {
        // Given
        String idToken = sign(signingKey, validClaims().build());

        // When
        Jwt jwt = jwtDecoder.decode(idToken);

        // Then
        assertThat(jwt.getSubject()).isEqualTo("provider-subject");
    }

    @Test
    @DisplayName("발급자가 설정과 다른 ID Token은 거절한다")
    void createDecoder_unknownIssuer_rejects() throws JOSEException {
        // Given
        String idToken = sign(signingKey, validClaims()
                .issuer("https://attacker.example.com")
                .build());

        // When & Then
        assertThatThrownBy(() -> jwtDecoder.decode(idToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("aud가 우리 앱이 아닌 ID Token은 거절한다")
    void createDecoder_unknownAudience_rejects() throws JOSEException {
        // Given
        String idToken = sign(signingKey, validClaims()
                .audience("other-client-id")
                .build());

        // When & Then
        assertThatThrownBy(() -> jwtDecoder.decode(idToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("허용 시계 오차 60초를 넘겨 만료된 ID Token은 거절한다")
    void createDecoder_expiredBeyondClockSkew_rejects() throws JOSEException {
        // Given
        Instant now = Instant.now();
        String idToken = sign(signingKey, validClaims()
                .issueTime(Date.from(now.minusSeconds(600)))
                .expirationTime(Date.from(now.minusSeconds(90)))
                .build());

        // When & Then
        assertThatThrownBy(() -> jwtDecoder.decode(idToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("만료됐어도 허용 시계 오차 60초 이내이면 디코딩한다")
    void createDecoder_expiredWithinClockSkew_decodes() throws JOSEException {
        // Given
        Instant now = Instant.now();
        String idToken = sign(signingKey, validClaims()
                .issueTime(Date.from(now.minusSeconds(600)))
                .expirationTime(Date.from(now.minusSeconds(30)))
                .build());

        // When
        Jwt jwt = jwtDecoder.decode(idToken);

        // Then
        assertThat(jwt.getSubject()).isEqualTo("provider-subject");
    }

    @Test
    @DisplayName("iat가 없는 ID Token은 거절한다")
    void createDecoder_missingIssuedAt_rejects() throws JOSEException {
        // Given
        String idToken = sign(signingKey, validClaims()
                .issueTime(null)
                .build());

        // When & Then
        assertThatThrownBy(() -> jwtDecoder.decode(idToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("같은 kid라도 공개키 목록에 없는 키로 서명한 ID Token은 거절한다")
    void createDecoder_signedWithOtherKey_rejects() throws JOSEException {
        // Given
        RSAKey otherKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        String idToken = sign(otherKey, validClaims().build());

        // When & Then
        assertThatThrownBy(() -> jwtDecoder.decode(idToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("RS256이 아닌 HS256으로 서명한 ID Token은 거절한다")
    void createDecoder_hs256Signature_rejects() throws JOSEException {
        // Given
        SignedJWT signedJwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(KEY_ID).build(),
                validClaims().build());
        signedJwt.sign(new MACSigner("0123456789abcdef0123456789abcdef"));

        // When & Then
        assertThatThrownBy(() -> jwtDecoder.decode(signedJwt.serialize()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("공개키 목록에 없는 kid의 ID Token은 거절한다")
    void createDecoder_unknownKeyId_rejects() throws JOSEException {
        // Given
        RSAKey unknownKey = new RSAKeyGenerator(2048).keyID("unknown-key").generate();
        String idToken = sign(unknownKey, validClaims().build());

        // When & Then
        assertThatThrownBy(() -> jwtDecoder.decode(idToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("캐시에 있는 kid의 ID Token이 반복되면 공개키 목록은 한 번만 조회한다")
    void createDecoder_repeatedKnownKeyId_fetchesJwkSetOnce() throws JOSEException {
        // Given
        String idToken = sign(signingKey, validClaims().build());

        // When
        jwtDecoder.decode(idToken);
        jwtDecoder.decode(idToken);
        jwtDecoder.decode(idToken);

        // Then
        assertThat(resourceRetriever.retrievalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("캐시에 없는 kid의 ID Token이 반복돼도 30초 구간 안에서는 공개키 목록을 최대 2회만 조회한다")
    void createDecoder_repeatedUnknownKeyId_limitsJwkSetRefetch() throws JOSEException {
        // Given
        jwtDecoder.decode(sign(signingKey, validClaims().build()));
        RSAKey unknownKey = new RSAKeyGenerator(2048).keyID("unknown-key").generate();
        String unknownKeyIdToken = sign(unknownKey, validClaims().build());

        // When
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> jwtDecoder.decode(unknownKeyIdToken))
                    .isInstanceOf(JwtException.class);
        }

        // Then
        assertThat(resourceRetriever.retrievalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("재조회 제한에 걸린 뒤에도 캐시에 있는 kid의 ID Token은 디코딩한다")
    void createDecoder_refetchLimitReached_decodesKnownKeyId() throws JOSEException {
        // Given
        String idToken = sign(signingKey, validClaims().build());
        jwtDecoder.decode(idToken);
        RSAKey unknownKey = new RSAKeyGenerator(2048).keyID("unknown-key").generate();
        String unknownKeyIdToken = sign(unknownKey, validClaims().build());
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> jwtDecoder.decode(unknownKeyIdToken))
                    .isInstanceOf(JwtException.class);
        }

        // When
        Jwt jwt = jwtDecoder.decode(idToken);

        // Then
        assertThat(jwt.getSubject()).isEqualTo("provider-subject");
        assertThat(resourceRetriever.retrievalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("제공자가 새 키를 추가하면 새 kid의 첫 ID Token에서 공개키 목록을 다시 받아 디코딩한다")
    void createDecoder_rotatedKeyId_refetchesJwkSetAndDecodes() throws JOSEException {
        // Given
        jwtDecoder.decode(sign(signingKey, validClaims().build()));
        RSAKey rotatedKey = new RSAKeyGenerator(2048).keyID("rotated-key").generate();
        resourceRetriever.changeJwkSet(
                new JWKSet(List.of(signingKey.toPublicJWK(), rotatedKey.toPublicJWK())).toString());

        // When
        Jwt jwt = jwtDecoder.decode(sign(rotatedKey, validClaims().build()));

        // Then
        assertThat(jwt.getSubject()).isEqualTo("provider-subject");
        assertThat(resourceRetriever.retrievalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("공개키 목록 재조회 제한 구간은 30초다")
    void createJwkSource_refetchInterval_isThirtySeconds() {
        // Given
        JWKSetBasedJWKSource<SecurityContext> jwkSource = (JWKSetBasedJWKSource<SecurityContext>) OidcIdTokenDecoderFactory
                .createJwkSource(
                        JWK_SET_URI,
                        resourceRetriever);

        // When
        JWKSetSource<SecurityContext> sourceBehindCache = ((CachingJWKSetSource<SecurityContext>) jwkSource
                .getJWKSetSource()).getSource();

        // Then
        assertThat(sourceBehindCache)
                .isInstanceOfSatisfying(RateLimitedJWKSetSource.class,
                        rateLimitedSource -> assertThat(
                                rateLimitedSource.getMinTimeInterval()).isEqualTo(30_000L));
    }

    @Test
    @DisplayName("JWK Set URI 형식이 올바르지 않으면 디코더를 만들지 않는다")
    void createJwkSource_malformedUri_throwsException() {
        // When & Then
        assertThatThrownBy(() -> OidcIdTokenDecoderFactory.createJwkSource(
                "not a uri",
                resourceRetriever))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWK Set URI 형식이 올바르지 않습니다");
    }

    @Test
    @DisplayName("JWK Set 주소로 만든 디코더는 JSON Accept 헤더로 공개키 목록을 받아 ID Token을 검증한다")
    void createDecoder_jwkSetUri_fetchesJwkSetOverHttp() throws Exception {
        // Given
        AtomicReference<String> acceptHeader = new AtomicReference<>();
        byte[] jwkSet = new JWKSet(signingKey.toPublicJWK()).toString()
                .getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/certs", exchange -> {
            acceptHeader.set(exchange.getRequestHeaders().getFirst("Accept"));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwkSet.length);
            exchange.getResponseBody().write(jwkSet);
            exchange.close();
        });
        server.start();
        try {
            JwtDecoder httpDecoder = OidcIdTokenDecoderFactory.createDecoder(
                    PROVIDER_NAME,
                    ISSUER,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/certs",
                    AUDIENCE);

            // When
            Jwt jwt = httpDecoder.decode(sign(signingKey, validClaims().build()));

            // Then
            assertThat(jwt.getSubject()).isEqualTo("provider-subject");
            assertThat(acceptHeader.get())
                    .isEqualTo("application/json, application/jwk-set+json");
        } finally {
            server.stop(0);
        }
    }

    private JWTClaimsSet.Builder validClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject("provider-subject")
                .issueTime(Date.from(now.minusSeconds(10)))
                .expirationTime(Date.from(now.plusSeconds(300)));
    }

    private String sign(RSAKey key, JWTClaimsSet claims) throws JOSEException {
        SignedJWT signedJwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                claims);
        signedJwt.sign(new RSASSASigner(key));
        return signedJwt.serialize();
    }

    private static final class CountingResourceRetriever implements ResourceRetriever {

        private final AtomicReference<String> jwkSet;
        private final AtomicInteger retrievalCount = new AtomicInteger();

        private CountingResourceRetriever(String jwkSet) {
            this.jwkSet = new AtomicReference<>(jwkSet);
        }

        @Override
        public Resource retrieveResource(URL url) {
            retrievalCount.incrementAndGet();
            return new Resource(jwkSet.get(), "application/json");
        }

        private void changeJwkSet(String jwkSet) {
            this.jwkSet.set(jwkSet);
        }

        private int retrievalCount() {
            return retrievalCount.get();
        }
    }
}
