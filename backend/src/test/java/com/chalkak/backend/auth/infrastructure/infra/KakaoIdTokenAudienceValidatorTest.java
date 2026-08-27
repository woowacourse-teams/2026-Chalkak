package com.chalkak.backend.auth.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class KakaoIdTokenAudienceValidatorTest {

    @Test
    @DisplayName("Kakao ID Token의 audience가 허용된 네이티브 앱 키이면 검증에 성공한다")
    void validate_allowedAudience_succeeds() {
        // Given
        KakaoIdTokenAudienceValidator validator =
                new KakaoIdTokenAudienceValidator("kakao-native-app-key");
        Jwt jwt = createJwt(List.of("kakao-native-app-key"));

        // When
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // Then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Kakao ID Token의 audience가 허용된 네이티브 앱 키가 아니면 검증에 실패한다")
    void validate_unknownAudience_fails() {
        // Given
        KakaoIdTokenAudienceValidator validator =
                new KakaoIdTokenAudienceValidator("kakao-native-app-key");
        Jwt jwt = createJwt(List.of("unknown-app-key"));

        // When
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // Then
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Kakao ID Token에 audience가 여러 개이면 검증에 실패한다")
    void validate_multipleAudiences_fails() {
        // Given
        KakaoIdTokenAudienceValidator validator =
                new KakaoIdTokenAudienceValidator("kakao-native-app-key");
        Jwt jwt = createJwt(List.of("kakao-native-app-key", "other-app-key"));

        // When
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // Then
        assertThat(result.hasErrors()).isTrue();
    }

    private Jwt createJwt(List<String> audiences) {
        return Jwt.withTokenValue("kakao-id-token")
                .header("alg", "RS256")
                .subject("kakao-subject")
                .audience(audiences)
                .issuedAt(Instant.parse("2026-08-27T00:00:00Z"))
                .expiresAt(Instant.parse("2026-08-27T01:00:00Z"))
                .build();
    }
}
