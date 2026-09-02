package com.chalkak.backend.auth.infrastructure.infra.oidc.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.exception.UnauthorizedException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

class KakaoIdTokenVerifierTest {

    @Test
    @DisplayName("Kakao ID Token의 subject와 이메일을 소셜 식별 정보로 반환한다")
    void verify_validIdToken_returnsSocialIdentity() {
        // Given
        Jwt jwt = Jwt.withTokenValue("kakao-id-token")
                .header("alg", "RS256")
                .subject("kakao-subject")
                .claim("email", "user@chalkak.test")
                .issuedAt(Instant.parse("2026-08-27T00:00:00Z"))
                .expiresAt(Instant.parse("2026-08-27T01:00:00Z"))
                .build();
        KakaoIdTokenVerifier verifier = new KakaoIdTokenVerifier(token -> jwt);

        // When
        VerifiedSocialIdentity identity = verifier.verify("kakao-id-token");

        // Then
        assertThat(identity.provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(identity.subject()).isEqualTo("kakao-subject");
        assertThat(identity.email()).isEqualTo("user@chalkak.test");
    }

    @Test
    @DisplayName("검증에 실패한 Kakao ID Token은 인증 실패로 변환한다")
    void verify_invalidIdToken_throwsUnauthorizedException() {
        // Given
        KakaoIdTokenVerifier verifier = new KakaoIdTokenVerifier(token -> {
            throw new JwtException("invalid token");
        });

        // When & Then
        assertThatThrownBy(() -> verifier.verify("invalid-kakao-id-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 Kakao ID Token입니다.");
    }

    @Test
    @DisplayName("subject가 없는 Kakao ID Token은 인증에 사용할 수 없다")
    void verify_missingSubject_throwsUnauthorizedException() {
        // Given
        Jwt jwt = Jwt.withTokenValue("kakao-id-token")
                .header("alg", "RS256")
                .issuedAt(Instant.parse("2026-08-27T00:00:00Z"))
                .expiresAt(Instant.parse("2026-08-27T01:00:00Z"))
                .build();
        KakaoIdTokenVerifier verifier = new KakaoIdTokenVerifier(token -> jwt);

        // When & Then
        assertThatThrownBy(() -> verifier.verify("kakao-id-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Kakao ID Token에 사용자 식별 정보가 없습니다.");
    }

    @Test
    @DisplayName("subject가 255자를 초과하는 Kakao ID Token은 인증에 사용할 수 없다")
    void verify_overlongSubject_throwsUnauthorizedException() {
        // Given
        Jwt jwt = Jwt.withTokenValue("kakao-id-token")
                .header("alg", "RS256")
                .subject("a".repeat(256))
                .issuedAt(Instant.parse("2026-08-27T00:00:00Z"))
                .expiresAt(Instant.parse("2026-08-27T01:00:00Z"))
                .build();
        KakaoIdTokenVerifier verifier = new KakaoIdTokenVerifier(token -> jwt);

        // When & Then
        assertThatThrownBy(() -> verifier.verify("kakao-id-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Kakao ID Token의 사용자 식별 정보가 너무 깁니다.");
    }

    @Test
    @DisplayName("subject가 255자이면 소셜 식별 정보로 반환한다")
    void verify_maxLengthSubject_returnsSocialIdentity() {
        // Given
        String subject = "a".repeat(255);
        Jwt jwt = Jwt.withTokenValue("kakao-id-token")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(Instant.parse("2026-08-27T00:00:00Z"))
                .expiresAt(Instant.parse("2026-08-27T01:00:00Z"))
                .build();
        KakaoIdTokenVerifier verifier = new KakaoIdTokenVerifier(token -> jwt);

        // When
        VerifiedSocialIdentity identity = verifier.verify("kakao-id-token");

        // Then
        assertThat(identity.subject()).isEqualTo(subject);
    }

    @Test
    @DisplayName("이메일이 없는 Kakao ID Token도 소셜 식별 정보로 반환한다")
    void verify_missingEmail_returnsIdentityWithoutEmail() {
        // Given
        Jwt jwt = Jwt.withTokenValue("kakao-id-token")
                .header("alg", "RS256")
                .subject("kakao-subject")
                .issuedAt(Instant.parse("2026-08-27T00:00:00Z"))
                .expiresAt(Instant.parse("2026-08-27T01:00:00Z"))
                .build();
        KakaoIdTokenVerifier verifier = new KakaoIdTokenVerifier(token -> jwt);

        // When
        VerifiedSocialIdentity identity = verifier.verify("kakao-id-token");

        // Then
        assertThat(identity.email()).isNull();
    }
}
