package com.chalkak.backend.auth.infrastructure.infra.oidc;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.infrastructure.infra.oidc.apple.AppleOidcProperties;
import com.chalkak.backend.auth.infrastructure.infra.oidc.google.GoogleOidcProperties;
import com.chalkak.backend.auth.infrastructure.infra.oidc.kakao.KakaoOidcProperties;
import com.chalkak.backend.auth.service.IdTokenVerifier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Configuration
@EnableConfigurationProperties({
        GoogleOidcProperties.class,
        KakaoOidcProperties.class,
        AppleOidcProperties.class
})
public class OidcIdTokenConfig {

    private static final String GOOGLE_PROVIDER_NAME = "Google";
    private static final String KAKAO_PROVIDER_NAME = "Kakao";
    private static final String APPLE_PROVIDER_NAME = "Apple";

    @Bean
    public JwtDecoder googleJwtDecoder(GoogleOidcProperties properties) {
        return OidcIdTokenDecoderFactory.createDecoder(
                GOOGLE_PROVIDER_NAME,
                properties.issuer(),
                properties.jwkSetUri(),
                properties.clientId());
    }

    @Bean
    public IdTokenVerifier googleIdTokenVerifier(
            @Qualifier("googleJwtDecoder") JwtDecoder jwtDecoder
    ) {
        return new OidcIdTokenVerifier(
                SocialProvider.GOOGLE,
                GOOGLE_PROVIDER_NAME,
                jwtDecoder,
                OidcEmailPolicy.VERIFIED_ONLY);
    }

    @Bean
    public JwtDecoder kakaoJwtDecoder(KakaoOidcProperties properties) {
        return OidcIdTokenDecoderFactory.createDecoder(
                KAKAO_PROVIDER_NAME,
                properties.issuer(),
                properties.jwkSetUri(),
                properties.appKey());
    }

    @Bean
    public IdTokenVerifier kakaoIdTokenVerifier(
            @Qualifier("kakaoJwtDecoder") JwtDecoder jwtDecoder
    ) {
        return new OidcIdTokenVerifier(
                SocialProvider.KAKAO,
                KAKAO_PROVIDER_NAME,
                jwtDecoder,
                OidcEmailPolicy.AS_PROVIDED);
    }

    @Bean
    public JwtDecoder appleJwtDecoder(AppleOidcProperties properties) {
        return OidcIdTokenDecoderFactory.createDecoder(
                APPLE_PROVIDER_NAME,
                properties.issuer(),
                properties.jwkSetUri(),
                properties.clientId());
    }

    @Bean
    public IdTokenVerifier appleIdTokenVerifier(
            @Qualifier("appleJwtDecoder") JwtDecoder jwtDecoder
    ) {
        return new OidcIdTokenVerifier(
                SocialProvider.APPLE,
                APPLE_PROVIDER_NAME,
                jwtDecoder,
                OidcEmailPolicy.VERIFIED_ONLY);
    }
}
