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

    @Bean
    public JwtDecoder googleJwtDecoder(GoogleOidcProperties properties) {
        return OidcIdTokenDecoderFactory.createDecoder(
                SocialProvider.GOOGLE,
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
                jwtDecoder,
                OidcEmailPolicy.VERIFIED_ONLY);
    }

    @Bean
    public JwtDecoder kakaoJwtDecoder(KakaoOidcProperties properties) {
        return OidcIdTokenDecoderFactory.createDecoder(
                SocialProvider.KAKAO,
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
                jwtDecoder,
                OidcEmailPolicy.AS_PROVIDED);
    }

    @Bean
    public JwtDecoder appleJwtDecoder(AppleOidcProperties properties) {
        return OidcIdTokenDecoderFactory.createDecoder(
                SocialProvider.APPLE,
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
                jwtDecoder,
                OidcEmailPolicy.VERIFIED_ONLY);
    }
}
