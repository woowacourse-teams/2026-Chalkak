package com.chalkak.backend.auth.infrastructure.infra.oidc.google;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class GoogleIdTokenAudienceValidatorTest {

    @Test
    @DisplayName("Google ID Token의 audience가 허용된 Client ID이면 검증에 성공한다")
    void validate_allowedAudience_succeeds() {
        // Given
        GoogleIdTokenAudienceValidator validator =
                new GoogleIdTokenAudienceValidator("backend-client-id");
        Jwt jwt = createJwt(List.of("backend-client-id"));

        // When
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // Then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Google ID Token의 audience가 허용된 Client ID가 아니면 검증에 실패한다")
    void validate_unknownAudience_fails() {
        // Given
        GoogleIdTokenAudienceValidator validator =
                new GoogleIdTokenAudienceValidator("backend-client-id");
        Jwt jwt = createJwt(List.of("unknown-client-id"));

        // When
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // Then
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Google ID Token에 audience가 여러 개이면 검증에 실패한다")
    void validate_multipleAudiences_fails() {
        // Given
        GoogleIdTokenAudienceValidator validator =
                new GoogleIdTokenAudienceValidator("backend-client-id");
        Jwt jwt = createJwt(List.of("backend-client-id", "other-client-id"));

        // When
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // Then
        assertThat(result.hasErrors()).isTrue();
    }

    private Jwt createJwt(List<String> audiences) {
        return Jwt.withTokenValue("google-id-token")
                .header("alg", "RS256")
                .subject("google-subject")
                .audience(audiences)
                .issuedAt(Instant.parse("2026-08-25T00:00:00Z"))
                .expiresAt(Instant.parse("2026-08-25T01:00:00Z"))
                .build();
    }
}
