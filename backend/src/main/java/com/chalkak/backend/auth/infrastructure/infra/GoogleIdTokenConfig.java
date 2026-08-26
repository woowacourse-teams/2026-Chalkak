package com.chalkak.backend.auth.infrastructure.infra;

import com.chalkak.backend.auth.service.IdTokenVerifier;
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
@EnableConfigurationProperties(GoogleOidcProperties.class)
public class GoogleIdTokenConfig {

    @Bean
    public JwtDecoder googleJwtDecoder(GoogleOidcProperties properties) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
                .withJwkSetUri(properties.jwkSetUri())
                .build();
        OAuth2TokenValidator<Jwt> issuerAndTimestampValidator =
                JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> audienceValidator =
                new GoogleIdTokenAudienceValidator(properties.clientId());
        OAuth2TokenValidator<Jwt> requiredClaimsValidator =
                new OidcIdTokenClaimsValidator();
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerAndTimestampValidator,
                audienceValidator,
                requiredClaimsValidator));
        return jwtDecoder;
    }

    @Bean
    public IdTokenVerifier googleIdTokenVerifier(
            @Qualifier("googleJwtDecoder") JwtDecoder jwtDecoder) {
        return new GoogleIdTokenVerifier(jwtDecoder);
    }
}
