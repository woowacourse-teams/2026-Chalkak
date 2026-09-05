package com.chalkak.backend.auth.infrastructure.infra.oidc.apple;

import com.chalkak.backend.auth.infrastructure.infra.oidc.OidcIdTokenClaimsValidator;
import com.chalkak.backend.auth.service.AppleIdTokenVerifier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@EnableConfigurationProperties(AppleOidcProperties.class)
public class AppleIdTokenConfig {

    @Bean
    public JwtDecoder appleJwtDecoder(AppleOidcProperties properties) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
                .withJwkSetUri(properties.jwkSetUri())
                .build();
        OAuth2TokenValidator<Jwt> issuerAndTimestampValidator =
                JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> audienceValidator =
                new AppleIdTokenAudienceValidator(properties.clientId());
        OAuth2TokenValidator<Jwt> requiredClaimsValidator =
                new OidcIdTokenClaimsValidator();
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerAndTimestampValidator,
                audienceValidator,
                requiredClaimsValidator));
        return jwtDecoder;
    }

    @Bean
    public AppleIdTokenVerifier appleIdTokenVerifier(
            @Qualifier("appleJwtDecoder") JwtDecoder jwtDecoder
    ) {
        return new AppleOidcIdTokenVerifier(jwtDecoder);
    }
}
