package com.chalkak.backend.auth.infrastructure.infra.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class OidcIdTokenClaimsValidatorTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    @Test
    @DisplayName("exp가 없는 ID Token은 검증에 실패한다")
    void validate_missingExpiration_fails() {
        // Given
        OidcIdTokenClaimsValidator validator = new OidcIdTokenClaimsValidator(
                Clock.fixed(NOW, ZoneOffset.UTC));
        Jwt jwt = Jwt.withTokenValue("id-token")
                .header("alg", "RS256")
                .subject("subject")
                .issuedAt(NOW)
                .build();

        // When
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // Then
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("iat가 없는 ID Token은 검증에 실패한다")
    void validate_missingIssuedAt_fails() {
        // Given
        OidcIdTokenClaimsValidator validator = new OidcIdTokenClaimsValidator(
                Clock.fixed(NOW, ZoneOffset.UTC));
        Jwt jwt = Jwt.withTokenValue("id-token")
                .header("alg", "RS256")
                .subject("subject")
                .expiresAt(NOW.plusSeconds(3600))
                .build();

        // When
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // Then
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("iat가 현재보다 60초를 초과해 미래이면 검증에 실패한다")
    void validate_issuedAtBeyondClockSkew_fails() {
        // Given
        OidcIdTokenClaimsValidator validator = new OidcIdTokenClaimsValidator(
                Clock.fixed(NOW, ZoneOffset.UTC));
        Jwt jwt = Jwt.withTokenValue("id-token")
                .header("alg", "RS256")
                .subject("subject")
                .issuedAt(NOW.plusSeconds(61))
                .expiresAt(NOW.plusSeconds(3600))
                .build();

        // When
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // Then
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("iat가 현재보다 60초 미래이면 시계 오차로 허용한다")
    void validate_issuedAtAtClockSkew_succeeds() {
        // Given
        OidcIdTokenClaimsValidator validator = new OidcIdTokenClaimsValidator(
                Clock.fixed(NOW, ZoneOffset.UTC));
        Jwt jwt = Jwt.withTokenValue("id-token")
                .header("alg", "RS256")
                .subject("subject")
                .issuedAt(NOW.plusSeconds(60))
                .expiresAt(NOW.plusSeconds(3600))
                .build();

        // When
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // Then
        assertThat(result.hasErrors()).isFalse();
    }
}
