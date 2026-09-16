package com.chalkak.backend.auth.infrastructure.infra.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.JWKSetBasedJWKSource;
import com.nimbusds.jose.jwk.source.JWKSetCacheRefreshEvaluator;
import com.nimbusds.jose.jwk.source.JWKSetSource;
import com.nimbusds.jose.jwk.source.RateLimitReachedException;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.Resource;
import com.nimbusds.jose.util.ResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

@ExtendWith(OutputCaptureExtension.class)
class OidcIdTokenDecoderFactoryTest {

    private static final SocialProvider PROVIDER = SocialProvider.GOOGLE;
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
                PROVIDER,
                ISSUER,
                AUDIENCE,
                OidcIdTokenDecoderFactory.createJwkSource(
                        PROVIDER,
                        JWK_SET_URI,
                        resourceRetriever));
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
    @DisplayName("aud가 없는 ID Token은 검증 실패로 거절한다")
    void createDecoder_missingAudience_rejects() throws JOSEException {
        // Given
        String idToken = sign(signingKey, validClaims()
                .audience((String) null)
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
    @DisplayName("재조회 제한에 걸리면 제공자 이름과 함께 경고 로그를 구간마다 한 번만 남긴다")
    void createDecoder_refetchLimitReachedRepeatedly_logsWarningOncePerWindow(CapturedOutput output)
            throws JOSEException {
        // Given
        jwtDecoder.decode(sign(signingKey, validClaims().build()));
        RSAKey unknownKey = new RSAKeyGenerator(2048).keyID("unknown-key").generate();
        String unknownKeyIdToken = sign(unknownKey, validClaims().build());

        // When
        for (int attempt = 0; attempt < 4; attempt++) {
            assertThatThrownBy(() -> jwtDecoder.decode(unknownKeyIdToken))
                    .isInstanceOf(JwtException.class);
        }

        // Then
        assertThat(output.getOut())
                .containsOnlyOnce("GOOGLE 공개키 목록 재조회 제한에 걸려 조회 없이 ID Token 검증에 실패했습니다.");
    }

    @Test
    @DisplayName("공개키 목록 조회가 한 번 실패하면 같은 조회 안에서 다시 시도해 디코딩한다")
    void createDecoder_transientRetrievalFailure_retriesAndDecodes(CapturedOutput output)
            throws JOSEException {
        // Given
        resourceRetriever.failNextRetrievals(1);

        // When
        Jwt jwt = jwtDecoder.decode(sign(signingKey, validClaims().build()));

        // Then
        assertThat(jwt.getSubject()).isEqualTo("provider-subject");
        assertThat(resourceRetriever.retrievalCount()).isEqualTo(2);
        assertThat(output.getOut()).contains("GOOGLE 공개키 목록 조회에 실패해 한 번 더 시도합니다.");
    }

    @Test
    @DisplayName("공개키 목록을 한 번도 받지 못한 채 재시도까지 실패하면 제공자가 회복돼도 남은 구간 동안 거절한다")
    void createDecoder_retrievalFailsBeforeFirstSuccess_rejectsUntilRefetchWindowEnds()
            throws JOSEException {
        // Given
        String idToken = sign(signingKey, validClaims().build());
        resourceRetriever.failNextRetrievals(4);
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> jwtDecoder.decode(idToken))
                    .isInstanceOf(JwtException.class);
        }

        // When & Then
        assertThatThrownBy(() -> jwtDecoder.decode(idToken))
                .isInstanceOf(JwtException.class);
        assertThat(resourceRetriever.retrievalCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("캐시 만료 직전 재조회 실패로 제한을 소진해도 이전 목록을 다시 캐시해 만료 후 조회가 막히지 않는다")
    void createJwkSource_refreshFailuresBeforeCacheExpiry_keepsServingPreviousJwkSet(
            CapturedOutput output
    ) throws Exception {
        // Given
        JWKSetSource<SecurityContext> source = jwkSetSourceWithInjectableTime();
        long firstRetrievedAt = 1_000_000L;
        source.getJWKSet(JWKSetCacheRefreshEvaluator.noRefresh(), firstRetrievedAt, null);
        long refreshFailedAt = firstRetrievedAt + Duration.ofSeconds(299).toMillis();
        resourceRetriever.failNextRetrievals(4);
        for (int attempt = 0; attempt < 2; attempt++) {
            refreshUnknownKeyId(source, refreshFailedAt);
        }
        assertThatThrownBy(() -> refreshUnknownKeyId(source, refreshFailedAt))
                .isInstanceOf(RateLimitReachedException.class);

        // When
        JWKSet jwkSet = source.getJWKSet(
                JWKSetCacheRefreshEvaluator.noRefresh(),
                firstRetrievedAt + Duration.ofSeconds(300).toMillis() + 1,
                null);

        // Then
        assertThat(jwkSet.getKeyByKeyId(KEY_ID)).isNotNull();
        assertThat(resourceRetriever.retrievalCount()).isEqualTo(5);
        assertThat(output.getOut()).contains("GOOGLE 공개키 목록을 받지 못해 이전에 받은 목록을 사용합니다.");
    }

    @Test
    @DisplayName("JWK Set URI 형식이 올바르지 않으면 디코더를 만들지 않는다")
    void createJwkSource_malformedUri_throwsException() {
        // When & Then
        assertThatThrownBy(() -> OidcIdTokenDecoderFactory.createJwkSource(
                PROVIDER,
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
                    PROVIDER,
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

    /**
     * 캐시·재조회 제한은 조회 시각을 기준으로 동작한다. 디코더는 현재 시각을 쓰므로, 시각을 넘겨 줄 수 있는 내부 목록 소스를 꺼내 쓴다.
     */
    private JWKSetSource<SecurityContext> jwkSetSourceWithInjectableTime() {
        JWKSetBasedJWKSource<SecurityContext> jwkSource = (JWKSetBasedJWKSource<SecurityContext>) OidcIdTokenDecoderFactory
                .createJwkSource(PROVIDER, JWK_SET_URI, resourceRetriever);
        return jwkSource.getJWKSetSource();
    }

    /** 디코더가 캐시에 없는 kid를 만났을 때처럼, 캐시된 목록을 확인한 뒤 그 목록이 그대로면 다시 조회하게 한다. */
    private void refreshUnknownKeyId(JWKSetSource<SecurityContext> source, long currentTime)
            throws Exception {
        JWKSet cached = source.getJWKSet(JWKSetCacheRefreshEvaluator.noRefresh(), currentTime,
                null);
        source.getJWKSet(JWKSetCacheRefreshEvaluator.referenceComparison(cached), currentTime,
                null);
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
        private final AtomicInteger remainingFailures = new AtomicInteger();

        private CountingResourceRetriever(String jwkSet) {
            this.jwkSet = new AtomicReference<>(jwkSet);
        }

        @Override
        public Resource retrieveResource(URL url) throws IOException {
            retrievalCount.incrementAndGet();
            if (remainingFailures.getAndUpdate(count -> Math.max(0, count - 1)) > 0) {
                throw new IOException("temporary network failure");
            }
            return new Resource(jwkSet.get(), "application/json");
        }

        private void failNextRetrievals(int count) {
            remainingFailures.set(count);
        }

        private void changeJwkSet(String jwkSet) {
            this.jwkSet.set(jwkSet);
        }

        private int retrievalCount() {
            return retrievalCount.get();
        }
    }
}
