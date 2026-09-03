package com.chalkak.backend.auth.infrastructure.infra.oidc.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.exception.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

class AppleOidcIdTokenVerifierTest {

    private static final String RAW_NONCE = "raw-nonce";

    @Test
    @DisplayName("Apple ID Token의 subject와 검증된 이메일을 소셜 식별 정보로 반환한다")
    void verify_validIdToken_returnsSocialIdentity() {
        // Given
        Jwt jwt = createJwt(
                "apple-subject",
                hash(RAW_NONCE),
                "user@privaterelay.appleid.com",
                true);
        AppleOidcIdTokenVerifier verifier = new AppleOidcIdTokenVerifier(token -> jwt);

        // When
        VerifiedSocialIdentity identity = verifier.verify("apple-id-token", RAW_NONCE);

        // Then
        assertThat(identity.provider()).isEqualTo(SocialProvider.APPLE);
        assertThat(identity.subject()).isEqualTo("apple-subject");
        assertThat(identity.email()).isEqualTo("user@privaterelay.appleid.com");
    }

    @Test
    @DisplayName("문자열 true로 표시된 Apple 검증 이메일도 소셜 식별 정보에 포함한다")
    void verify_stringVerifiedEmail_returnsEmail() {
        // Given
        Jwt jwt = createJwt(
                "apple-subject",
                hash(RAW_NONCE),
                "user@privaterelay.appleid.com",
                "true");
        AppleOidcIdTokenVerifier verifier = new AppleOidcIdTokenVerifier(token -> jwt);

        // When
        VerifiedSocialIdentity identity = verifier.verify("apple-id-token", RAW_NONCE);

        // Then
        assertThat(identity.email()).isEqualTo("user@privaterelay.appleid.com");
    }

    @Test
    @DisplayName("검증되지 않은 Apple 이메일은 소셜 식별 정보에서 제외한다")
    void verify_unverifiedEmail_returnsIdentityWithoutEmail() {
        // Given
        Jwt jwt = createJwt(
                "apple-subject",
                hash(RAW_NONCE),
                "user@privaterelay.appleid.com",
                false);
        AppleOidcIdTokenVerifier verifier = new AppleOidcIdTokenVerifier(token -> jwt);

        // When
        VerifiedSocialIdentity identity = verifier.verify("apple-id-token", RAW_NONCE);

        // Then
        assertThat(identity.email()).isNull();
    }

    @Test
    @DisplayName("검증에 실패한 Apple ID Token은 인증 실패로 변환한다")
    void verify_invalidIdToken_throwsUnauthorizedException() {
        // Given
        AppleOidcIdTokenVerifier verifier = new AppleOidcIdTokenVerifier(token -> {
            throw new JwtException("invalid token");
        });

        // When & Then
        assertThatThrownBy(() -> verifier.verify("invalid-apple-id-token", RAW_NONCE))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 Apple ID Token입니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("rawNonce가 없으면 Apple 사용자 인증을 거부한다")
    void verify_missingRawNonce_throwsUnauthorizedException(String rawNonce) {
        // Given
        Jwt jwt = createJwt("apple-subject", hash(RAW_NONCE), null, null);
        AppleOidcIdTokenVerifier verifier = new AppleOidcIdTokenVerifier(token -> jwt);

        // When & Then
        assertThatThrownBy(() -> verifier.verify("apple-id-token", rawNonce))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Apple 로그인 nonce가 필요합니다.");
    }

    @Test
    @DisplayName("Apple ID Token에 nonce가 없으면 사용자 인증을 거부한다")
    void verify_missingNonceClaim_throwsUnauthorizedException() {
        // Given
        Jwt jwt = createJwtWithoutNonce("apple-subject");
        AppleOidcIdTokenVerifier verifier = new AppleOidcIdTokenVerifier(token -> jwt);

        // When & Then
        assertThatThrownBy(() -> verifier.verify("apple-id-token", RAW_NONCE))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Apple ID Token nonce가 일치하지 않습니다.");
    }

    @Test
    @DisplayName("Apple ID Token의 nonce가 rawNonce의 해시와 다르면 사용자 인증을 거부한다")
    void verify_mismatchedNonce_throwsUnauthorizedException() {
        // Given
        Jwt jwt = createJwt("apple-subject", hash("other-raw-nonce"), null, null);
        AppleOidcIdTokenVerifier verifier = new AppleOidcIdTokenVerifier(token -> jwt);

        // When & Then
        assertThatThrownBy(() -> verifier.verify("apple-id-token", RAW_NONCE))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Apple ID Token nonce가 일치하지 않습니다.");
    }

    @Test
    @DisplayName("subject가 없는 Apple ID Token은 인증에 사용할 수 없다")
    void verify_missingSubject_throwsUnauthorizedException() {
        // Given
        Jwt jwt = createJwt(null, hash(RAW_NONCE), null, null);
        AppleOidcIdTokenVerifier verifier = new AppleOidcIdTokenVerifier(token -> jwt);

        // When & Then
        assertThatThrownBy(() -> verifier.verify("apple-id-token", RAW_NONCE))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Apple ID Token에 사용자 식별 정보가 없습니다.");
    }

    @Test
    @DisplayName("subject가 255자를 초과하는 Apple ID Token은 인증에 사용할 수 없다")
    void verify_overlongSubject_throwsUnauthorizedException() {
        // Given
        Jwt jwt = createJwt("a".repeat(256), hash(RAW_NONCE), null, null);
        AppleOidcIdTokenVerifier verifier = new AppleOidcIdTokenVerifier(token -> jwt);

        // When & Then
        assertThatThrownBy(() -> verifier.verify("apple-id-token", RAW_NONCE))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Apple ID Token의 사용자 식별 정보가 너무 깁니다.");
    }

    @Test
    @DisplayName("subject가 255자이면 Apple 소셜 식별 정보로 반환한다")
    void verify_maxLengthSubject_returnsSocialIdentity() {
        // Given
        String subject = "a".repeat(255);
        Jwt jwt = createJwt(subject, hash(RAW_NONCE), null, null);
        AppleOidcIdTokenVerifier verifier = new AppleOidcIdTokenVerifier(token -> jwt);

        // When
        VerifiedSocialIdentity identity = verifier.verify("apple-id-token", RAW_NONCE);

        // Then
        assertThat(identity.subject()).isEqualTo(subject);
    }

    private Jwt createJwt(
            String subject,
            String nonce,
            String email,
            Object emailVerified
    ) {
        Jwt.Builder builder = Jwt.withTokenValue("apple-id-token")
                .header("alg", "RS256")
                .claim("nonce", nonce)
                .issuedAt(Instant.parse("2026-08-25T00:00:00Z"))
                .expiresAt(Instant.parse("2026-08-25T01:00:00Z"));
        if (subject != null) {
            builder.subject(subject);
        }
        if (email != null) {
            builder.claim("email", email);
        }
        if (emailVerified != null) {
            builder.claim("email_verified", emailVerified);
        }
        return builder.build();
    }

    private Jwt createJwtWithoutNonce(String subject) {
        return Jwt.withTokenValue("apple-id-token")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(Instant.parse("2026-08-25T00:00:00Z"))
                .expiresAt(Instant.parse("2026-08-25T01:00:00Z"))
                .build();
    }

    private String hash(String rawNonce) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawNonce.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
