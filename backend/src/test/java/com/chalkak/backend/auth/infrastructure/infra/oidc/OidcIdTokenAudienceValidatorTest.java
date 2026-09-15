package com.chalkak.backend.auth.infrastructure.infra.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class OidcIdTokenAudienceValidatorTest {

    private final OidcIdTokenAudienceValidator validator = new OidcIdTokenAudienceValidator(
            "Google", "backend-client-id");

    @Test
    @DisplayName("audience가 허용된 값 하나이면 검증에 성공한다")
    void validate_singleAllowedAudience_succeeds() {
        // Given
        Jwt jwt = createJwt(List.of("backend-client-id"));

        // When
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // Then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("audience가 허용된 값이 아니면 검증에 실패한다")
    void validate_unknownAudience_fails() {
        // Given
        Jwt jwt = createJwt(List.of("unknown-client-id"));

        // When
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // Then
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("허용된 값이 포함돼도 audience가 여러 개이면 검증에 실패한다")
    void validate_multipleAudiencesIncludingAllowed_fails() {
        // Given
        Jwt jwt = createJwt(List.of("backend-client-id", "other-client-id"));

        // When
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // Then
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("검증 실패 메시지에 제공자 이름을 담는다")
    void validate_unknownAudience_describesProvider() {
        // Given
        Jwt jwt = createJwt(List.of("unknown-client-id"));

        // When
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // Then
        assertThat(result.getErrors())
                .extracting(OAuth2Error::getDescription)
                .containsExactly("Google ID Token audience가 허용되지 않았습니다.");
    }

    private Jwt createJwt(List<String> audiences) {
        return Jwt.withTokenValue("id-token")
                .header("alg", "RS256")
                .subject("subject")
                .audience(audiences)
                .issuedAt(Instant.parse("2026-08-25T00:00:00Z"))
                .expiresAt(Instant.parse("2026-08-25T01:00:00Z"))
                .build();
    }
}
