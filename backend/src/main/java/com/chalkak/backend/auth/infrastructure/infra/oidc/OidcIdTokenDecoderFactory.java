package com.chalkak.backend.auth.infrastructure.infra.oidc;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.util.ResourceRetriever;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * 제공자별 ID Token 디코더를 같은 규칙으로 조립한다. 제공자마다 다른 것은 발급자, 공개키 주소, 우리 앱의 aud 값뿐이라 검증
 * 구성을 한 곳에 두고, 규칙이 한 제공자에서만 약해지지 않게 한다.
 *
 * <p>
 * 공개키 목록은 캐시 5분, 연결·읽기 타임아웃 각 0.5초, JSON·JWK Set Accept 헤더로 가져오고 캐시 선갱신은 사용하지
 * 않는다.
 *
 * <p>
 * 캐시에 없는 kid의 토큰이 올 때마다 공개키 목록을 다시 받으면, 임의 kid 토큰을 반복해 보내는 것만으로 제공자에 요청을 계속 보낼
 * 수 있다. 그래서 재조회는 제공자별로 30초 구간마다 최대 2회만 허용하고, 넘친 요청은 공개키 목록 없이 검증 실패로 끝낸다. 캐시에
 * 있는 kid의 토큰은 재조회하지 않으므로 이 제한의 영향을 받지 않는다.
 */
final class OidcIdTokenDecoderFactory {

    private static final String JWK_SET_ACCEPT_TYPES = MediaType.APPLICATION_JSON_VALUE
            + ", application/jwk-set+json";
    private static final long JWK_SET_REFETCH_INTERVAL_MILLIS = Duration.ofSeconds(30).toMillis();

    private OidcIdTokenDecoderFactory() {
    }

    static JwtDecoder createDecoder(
            String providerName,
            String issuer,
            String jwkSetUri,
            String audience
    ) {
        DefaultResourceRetriever resourceRetriever = new DefaultResourceRetriever(
                JWKSourceBuilder.DEFAULT_HTTP_CONNECT_TIMEOUT,
                JWKSourceBuilder.DEFAULT_HTTP_READ_TIMEOUT,
                JWKSourceBuilder.DEFAULT_HTTP_SIZE_LIMIT);
        resourceRetriever.setHeaders(Map.of(HttpHeaders.ACCEPT, List.of(JWK_SET_ACCEPT_TYPES)));
        return createDecoder(
                providerName,
                issuer,
                audience,
                createJwkSource(jwkSetUri, resourceRetriever));
    }

    static JwtDecoder createDecoder(
            String providerName,
            String issuer,
            String audience,
            JWKSource<SecurityContext> jwkSource
    ) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSource(jwkSource).build();
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new OidcIdTokenAudienceValidator(providerName, audience),
                new OidcIdTokenClaimsValidator()));
        return jwtDecoder;
    }

    static JWKSource<SecurityContext> createJwkSource(
            String jwkSetUri,
            ResourceRetriever resourceRetriever
    ) {
        return JWKSourceBuilder.<SecurityContext>create(toUrl(jwkSetUri), resourceRetriever)
                .cache(
                        JWKSourceBuilder.DEFAULT_CACHE_TIME_TO_LIVE,
                        JWKSourceBuilder.DEFAULT_CACHE_REFRESH_TIMEOUT)
                .refreshAheadCache(false)
                .rateLimited(JWK_SET_REFETCH_INTERVAL_MILLIS)
                .build();
    }

    private static URL toUrl(String jwkSetUri) {
        try {
            return URI.create(jwkSetUri).toURL();
        } catch (IllegalArgumentException | MalformedURLException exception) {
            throw new IllegalArgumentException(
                    "JWK Set URI 형식이 올바르지 않습니다: " + jwkSetUri,
                    exception);
        }
    }
}
