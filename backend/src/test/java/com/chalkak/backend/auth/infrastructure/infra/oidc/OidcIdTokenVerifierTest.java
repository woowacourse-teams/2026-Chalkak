package com.chalkak.backend.auth.infrastructure.infra.oidc;

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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class OidcIdTokenVerifierTest {

    private static final String ID_TOKEN = "id-token";
    private static final String RAW_NONCE = "raw-nonce";

    @Test
    @DisplayName("nonce가 원본의 해시와 같으면 제공자와 subject, 이메일 정책에 맞는 이메일을 반환한다")
    void verify_matchingNonce_returnsSocialIdentity() {
        // Given
        Jwt jwt = jwtBuilder()
                .claim("email", "user@chalkak.test")
                .claim("email_verified", true)
                .build();
        OidcIdTokenVerifier verifier = verifier(SocialProvider.GOOGLE, jwt);

        // When
        VerifiedSocialIdentity identity = verifier.verify(ID_TOKEN, RAW_NONCE);

        // Then
        assertThat(identity.provider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(identity.subject()).isEqualTo("provider-subject");
        assertThat(identity.email()).isEqualTo("user@chalkak.test");
    }

    @ParameterizedTest
    @CsvSource({"GOOGLE, Google", "KAKAO, Kakao", "APPLE, Apple"})
    @DisplayName("모든 제공자는 ID Token의 nonce가 원본의 해시와 다르면 인증을 거부한다")
    void verify_mismatchedNonce_throwsUnauthorizedException(
            SocialProvider provider,
            String expectedDisplayName
    ) {
        // Given
        Jwt jwt = jwtBuilder()
                .claim("nonce", hash("other-raw-nonce"))
                .build();
        OidcIdTokenVerifier verifier = verifier(provider, jwt);

        // When & Then
        assertThatThrownBy(() -> verifier.verify(ID_TOKEN, RAW_NONCE))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(expectedDisplayName + " ID Token nonce가 일치하지 않습니다.");
    }

    @ParameterizedTest
    @CsvSource({"GOOGLE, Google", "KAKAO, Kakao", "APPLE, Apple"})
    @DisplayName("모든 제공자는 ID Token에 nonce가 없으면 인증을 거부한다")
    void verify_missingNonceClaim_throwsUnauthorizedException(
            SocialProvider provider,
            String expectedDisplayName
    ) {
        // Given
        Jwt jwt = Jwt.withTokenValue(ID_TOKEN)
                .header("alg", "RS256")
                .subject("provider-subject")
                .issuedAt(Instant.parse("2026-09-15T00:00:00Z"))
                .expiresAt(Instant.parse("2026-09-15T00:10:00Z"))
                .build();
        OidcIdTokenVerifier verifier = verifier(provider, jwt);

        // When & Then
        assertThatThrownBy(() -> verifier.verify(ID_TOKEN, RAW_NONCE))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(expectedDisplayName + " ID Token nonce가 일치하지 않습니다.");
    }

    @Test
    @DisplayName("nonce가 원본 해시의 대문자 hex이면 약속한 소문자 hex가 아니므로 인증을 거부한다")
    void verify_uppercaseHexNonce_throwsUnauthorizedException() {
        // Given
        Jwt jwt = jwtBuilder()
                .claim("nonce", hash(RAW_NONCE).toUpperCase(Locale.ROOT))
                .build();
        OidcIdTokenVerifier verifier = verifier(SocialProvider.KAKAO, jwt);

        // When & Then
        assertThatThrownBy(() -> verifier.verify(ID_TOKEN, RAW_NONCE))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Kakao ID Token nonce가 일치하지 않습니다.");
    }

    @Test
    @DisplayName("ID Token에 원본 nonce 자체가 담겨 있으면 해시가 아니므로 인증을 거부한다")
    void verify_rawNonceInClaim_throwsUnauthorizedException() {
        // Given
        Jwt jwt = jwtBuilder()
                .claim("nonce", RAW_NONCE)
                .build();
        OidcIdTokenVerifier verifier = verifier(SocialProvider.GOOGLE, jwt);

        // When & Then
        assertThatThrownBy(() -> verifier.verify(ID_TOKEN, RAW_NONCE))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Google ID Token nonce가 일치하지 않습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("원본 nonce가 없으면 ID Token을 디코딩하지 않고 인증을 거부한다")
    void verify_missingRawNonce_throwsUnauthorizedException(String rawNonce) {
        // Given
        AtomicBoolean decoded = new AtomicBoolean();
        OidcIdTokenVerifier verifier = new OidcIdTokenVerifier(
                SocialProvider.KAKAO,
                token -> {
                    decoded.set(true);
                    return jwtBuilder().build();
                },
                OidcEmailPolicy.AS_PROVIDED);

        // When & Then
        assertThatThrownBy(() -> verifier.verify(ID_TOKEN, rawNonce))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Kakao 로그인 nonce가 필요합니다.");
        assertThat(decoded).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("ID Token이 비어 있으면 인증을 거부한다")
    void verify_blankIdToken_throwsUnauthorizedException(String idToken) {
        // Given
        OidcIdTokenVerifier verifier = verifier(
                SocialProvider.APPLE,
                jwtBuilder().build());

        // When & Then
        assertThatThrownBy(() -> verifier.verify(idToken, RAW_NONCE))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 Apple ID Token입니다.");
    }

    @Test
    @DisplayName("디코더 검증에 실패한 ID Token은 인증 실패로 변환한다")
    void verify_decoderRejects_throwsUnauthorizedException() {
        // Given
        OidcIdTokenVerifier verifier = new OidcIdTokenVerifier(
                SocialProvider.GOOGLE,
                token -> {
                    throw new JwtException("invalid token");
                },
                OidcEmailPolicy.VERIFIED_ONLY);

        // When & Then
        assertThatThrownBy(() -> verifier.verify(ID_TOKEN, RAW_NONCE))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 Google ID Token입니다.");
    }

    @Test
    @DisplayName("subject가 없는 ID Token은 인증에 사용할 수 없다")
    void verify_missingSubject_throwsUnauthorizedException() {
        // Given
        Jwt jwt = Jwt.withTokenValue(ID_TOKEN)
                .header("alg", "RS256")
                .claim("nonce", hash(RAW_NONCE))
                .issuedAt(Instant.parse("2026-09-15T00:00:00Z"))
                .expiresAt(Instant.parse("2026-09-15T00:10:00Z"))
                .build();
        OidcIdTokenVerifier verifier = verifier(SocialProvider.KAKAO, jwt);

        // When & Then
        assertThatThrownBy(() -> verifier.verify(ID_TOKEN, RAW_NONCE))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Kakao ID Token에 사용자 식별 정보가 없습니다.");
    }

    @Test
    @DisplayName("subject가 255자를 초과하는 ID Token은 인증에 사용할 수 없다")
    void verify_overlongSubject_throwsUnauthorizedException() {
        // Given
        Jwt jwt = jwtBuilder()
                .subject("a".repeat(256))
                .build();
        OidcIdTokenVerifier verifier = verifier(SocialProvider.APPLE, jwt);

        // When & Then
        assertThatThrownBy(() -> verifier.verify(ID_TOKEN, RAW_NONCE))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Apple ID Token의 사용자 식별 정보가 너무 깁니다.");
    }

    @Test
    @DisplayName("subject가 255자이면 소셜 식별 정보로 반환한다")
    void verify_maxLengthSubject_returnsSocialIdentity() {
        // Given
        String subject = "a".repeat(255);
        Jwt jwt = jwtBuilder()
                .subject(subject)
                .build();
        OidcIdTokenVerifier verifier = verifier(SocialProvider.GOOGLE, jwt);

        // When
        VerifiedSocialIdentity identity = verifier.verify(ID_TOKEN, RAW_NONCE);

        // Then
        assertThat(identity.subject()).isEqualTo(subject);
    }

    private OidcIdTokenVerifier verifier(SocialProvider provider, Jwt jwt) {
        JwtDecoder jwtDecoder = token -> jwt;
        return new OidcIdTokenVerifier(
                provider,
                jwtDecoder,
                OidcEmailPolicy.VERIFIED_ONLY);
    }

    private Jwt.Builder jwtBuilder() {
        return Jwt.withTokenValue(ID_TOKEN)
                .header("alg", "RS256")
                .subject("provider-subject")
                .claim("nonce", hash(RAW_NONCE))
                .issuedAt(Instant.parse("2026-09-15T00:00:00Z"))
                .expiresAt(Instant.parse("2026-09-15T00:10:00Z"));
    }

    private String hash(String rawNonce) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(rawNonce.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
