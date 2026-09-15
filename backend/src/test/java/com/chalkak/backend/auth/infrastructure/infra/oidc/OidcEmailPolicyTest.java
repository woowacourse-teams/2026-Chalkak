package com.chalkak.backend.auth.infrastructure.infra.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;

class OidcEmailPolicyTest {

    private static final String EMAIL = "user@chalkak.test";

    @Test
    @DisplayName("확인된 이메일만 받는 정책은 email_verified가 boolean true이면 이메일을 반환한다")
    void extractEmail_verifiedOnlyWithBooleanTrue_returnsEmail() {
        // Given
        Jwt jwt = jwtBuilder()
                .claim("email", EMAIL)
                .claim("email_verified", true)
                .build();

        // When
        String email = OidcEmailPolicy.VERIFIED_ONLY.extractEmail(jwt);

        // Then
        assertThat(email).isEqualTo(EMAIL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE"})
    @DisplayName("확인된 이메일만 받는 정책은 email_verified가 문자열 true이면 이메일을 반환한다")
    void extractEmail_verifiedOnlyWithStringTrue_returnsEmail(String emailVerified) {
        // Given
        Jwt jwt = jwtBuilder()
                .claim("email", EMAIL)
                .claim("email_verified", emailVerified)
                .build();

        // When
        String email = OidcEmailPolicy.VERIFIED_ONLY.extractEmail(jwt);

        // Then
        assertThat(email).isEqualTo(EMAIL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "yes", ""})
    @DisplayName("확인된 이메일만 받는 정책은 email_verified가 참이 아니면 이메일을 버린다")
    void extractEmail_verifiedOnlyWithoutTrue_returnsNull(String emailVerified) {
        // Given
        Jwt jwt = jwtBuilder()
                .claim("email", EMAIL)
                .claim("email_verified", emailVerified)
                .build();

        // When
        String email = OidcEmailPolicy.VERIFIED_ONLY.extractEmail(jwt);

        // Then
        assertThat(email).isNull();
    }

    @Test
    @DisplayName("확인된 이메일만 받는 정책은 email_verified가 boolean false이거나 없으면 이메일을 버린다")
    void extractEmail_verifiedOnlyWithFalseOrMissing_returnsNull() {
        // Given
        Jwt falseVerified = jwtBuilder()
                .claim("email", EMAIL)
                .claim("email_verified", false)
                .build();
        Jwt missingVerified = jwtBuilder()
                .claim("email", EMAIL)
                .build();

        // When & Then
        assertThat(OidcEmailPolicy.VERIFIED_ONLY.extractEmail(falseVerified)).isNull();
        assertThat(OidcEmailPolicy.VERIFIED_ONLY.extractEmail(missingVerified)).isNull();
    }

    @Test
    @DisplayName("받은 이메일을 그대로 쓰는 정책은 email_verified와 관계없이 이메일을 반환한다")
    void extractEmail_asProvidedWithoutVerification_returnsEmail() {
        // Given
        Jwt jwt = jwtBuilder()
                .claim("email", EMAIL)
                .claim("email_verified", false)
                .build();

        // When
        String email = OidcEmailPolicy.AS_PROVIDED.extractEmail(jwt);

        // Then
        assertThat(email).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("받은 이메일을 그대로 쓰는 정책도 이메일이 없으면 이메일 없이 반환한다")
    void extractEmail_asProvidedWithoutEmail_returnsNull() {
        // Given
        Jwt jwt = jwtBuilder().build();

        // When
        String email = OidcEmailPolicy.AS_PROVIDED.extractEmail(jwt);

        // Then
        assertThat(email).isNull();
    }

    private Jwt.Builder jwtBuilder() {
        return Jwt.withTokenValue("id-token")
                .header("alg", "RS256")
                .subject("provider-subject")
                .issuedAt(Instant.parse("2026-09-15T00:00:00Z"))
                .expiresAt(Instant.parse("2026-09-15T00:10:00Z"));
    }
}
