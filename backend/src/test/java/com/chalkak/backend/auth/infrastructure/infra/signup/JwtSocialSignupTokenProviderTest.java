package com.chalkak.backend.auth.infrastructure.infra.signup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.auth.domain.IssuedSocialSignupToken;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.domain.VerifiedSocialSignupToken;
import com.chalkak.backend.exception.UnauthorizedException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

class JwtSocialSignupTokenProviderTest {

    private static final Instant NOW = Instant.parse("2026-08-26T00:00:00Z");
    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String SUBJECT = "google-subject";

    @Test
    @DisplayName("소셜 사용자와 업로드 식별자를 연결한 회원가입 토큰을 발급하고 검증한다")
    void issue_validIdentityAndUploadId_verifiesSignupToken() {
        // Given
        JwtSocialSignupTokenProvider provider = createProvider(Clock.fixed(
                NOW,
                ZoneOffset.UTC));
        VerifiedSocialIdentity identity = new VerifiedSocialIdentity(
                SocialProvider.GOOGLE,
                SUBJECT,
                "user@chalkak.test");
        UUID uploadId = UUID.randomUUID();

        // When
        IssuedSocialSignupToken issuedToken = provider.issue(identity, uploadId);
        VerifiedSocialSignupToken verifiedToken = provider.verify(
                issuedToken.value());

        // Then
        assertThat(issuedToken.value()).isNotBlank();
        assertThat(verifiedToken.provider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(verifiedToken.subject()).isEqualTo(SUBJECT);
        assertThat(verifiedToken.uploadId()).isEqualTo(uploadId);
        assertThat(verifiedToken.email()).isEqualTo("user@chalkak.test");
        assertThat(verifiedToken.tokenId()).isNotBlank();
        assertThat(verifiedToken.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(issuedToken.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("Apple 회원가입 토큰에는 공통 식별 정보만 포함한다")
    void issue_appleIdentity_containsNoRefreshToken() {
        // Given
        JwtSocialSignupTokenProvider provider = createProvider(Clock.fixed(
                NOW,
                ZoneOffset.UTC));
        VerifiedSocialIdentity identity = new VerifiedSocialIdentity(
                SocialProvider.APPLE,
                "apple-subject",
                "user@privaterelay.appleid.com");
        UUID uploadId = UUID.randomUUID();

        // When
        IssuedSocialSignupToken issuedToken = provider.issue(identity, uploadId);
        VerifiedSocialSignupToken verifiedToken = provider.verify(
                issuedToken.value());
        String payload = new String(
                Base64.getUrlDecoder().decode(issuedToken.value().split("\\.")[1]),
                StandardCharsets.UTF_8);

        // Then
        assertThat(verifiedToken.provider()).isEqualTo(SocialProvider.APPLE);
        assertThat(verifiedToken.subject()).isEqualTo("apple-subject");
        assertThat(verifiedToken.uploadId()).isEqualTo(uploadId);
        assertThat(verifiedToken.tokenId()).isNotBlank();
        assertThat(payload)
                .doesNotContain("appleEncryptedRefreshToken")
                .doesNotContain("encrypted-apple-refresh-token");
    }

    @Test
    @DisplayName("발급할 때마다 서로 다른 토큰 식별자를 부여한다")
    void issue_calledTwice_generatesDistinctTokenIds() {
        // Given
        JwtSocialSignupTokenProvider provider = createProvider(Clock.fixed(
                NOW,
                ZoneOffset.UTC));

        // When
        VerifiedSocialSignupToken first = provider.verify(
                provider.issue(identity(), UUID.randomUUID()).value());
        VerifiedSocialSignupToken second = provider.verify(
                provider.issue(identity(), UUID.randomUUID()).value());

        // Then
        assertThat(first.tokenId()).isNotEqualTo(second.tokenId());
    }

    @Test
    @DisplayName("소셜 제공자가 이메일을 제공하지 않아도 회원가입 토큰을 발급하고 검증한다")
    void issue_nullEmail_verifiesSignupTokenWithoutEmail() {
        // Given
        JwtSocialSignupTokenProvider provider = createProvider(Clock.fixed(
                NOW,
                ZoneOffset.UTC));
        VerifiedSocialIdentity identity = new VerifiedSocialIdentity(
                SocialProvider.GOOGLE,
                SUBJECT,
                null);

        // When
        IssuedSocialSignupToken issuedToken = provider.issue(
                identity,
                UUID.randomUUID());
        VerifiedSocialSignupToken verifiedToken = provider.verify(
                issuedToken.value());

        // Then
        assertThat(verifiedToken.email()).isNull();
    }

    @Test
    @DisplayName("서명이 변조된 회원가입 토큰은 검증할 수 없다")
    void verify_tamperedToken_throwsUnauthorizedException() {
        // Given
        JwtSocialSignupTokenProvider provider = createProvider(Clock.fixed(
                NOW,
                ZoneOffset.UTC));
        String token = provider.issue(identity(), UUID.randomUUID()).value();
        String[] tokenParts = token.split("\\.");
        char replacement = tokenParts[1].charAt(0) == 'A' ? 'B' : 'A';
        tokenParts[1] = replacement + tokenParts[1].substring(1);
        String tamperedToken = String.join(".", tokenParts);

        // When & Then
        assertInvalidSignupToken(() -> provider.verify(tamperedToken));
    }

    @Test
    @DisplayName("만료된 회원가입 토큰은 검증할 수 없다")
    void verify_expiredToken_throwsUnauthorizedException() {
        // Given
        JwtSocialSignupTokenProvider issuer = createProvider(Clock.fixed(
                NOW,
                ZoneOffset.UTC));
        String token = issuer.issue(identity(), UUID.randomUUID()).value();
        JwtSocialSignupTokenProvider verifier = createProvider(Clock.fixed(
                NOW.plus(Duration.ofMinutes(5)).plusSeconds(31),
                ZoneOffset.UTC));

        // When & Then
        assertInvalidSignupToken(() -> verifier.verify(token));
    }

    @Test
    @DisplayName("다른 발급자가 생성한 회원가입 토큰은 검증할 수 없다")
    void verify_wrongIssuer_throwsUnauthorizedException() {
        // Given
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        JwtSocialSignupTokenProvider issuer = createProvider(
                clock,
                "other-backend",
                "chalkak-social-signup",
                SECRET);
        String token = issuer.issue(identity(), UUID.randomUUID()).value();
        JwtSocialSignupTokenProvider verifier = createProvider(clock);

        // When & Then
        assertInvalidSignupToken(() -> verifier.verify(token));
    }

    @Test
    @DisplayName("다른 대상용 회원가입 토큰은 검증할 수 없다")
    void verify_wrongAudience_throwsUnauthorizedException() {
        // Given
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        JwtSocialSignupTokenProvider issuer = createProvider(
                clock,
                "chalkak-backend",
                "other-audience",
                SECRET);
        String token = issuer.issue(identity(), UUID.randomUUID()).value();
        JwtSocialSignupTokenProvider verifier = createProvider(clock);

        // When & Then
        assertInvalidSignupToken(() -> verifier.verify(token));
    }

    @Test
    @DisplayName("소셜 회원가입 용도가 아닌 토큰은 검증할 수 없다")
    void verify_wrongPurpose_throwsUnauthorizedException() {
        // Given
        JwtSocialSignupTokenProvider provider = createProvider(Clock.fixed(
                NOW,
                ZoneOffset.UTC));
        String token = createSignedToken("OTHER_PURPOSE", true);

        // When & Then
        assertInvalidSignupToken(() -> provider.verify(token));
    }

    @Test
    @DisplayName("토큰 식별자가 없는 회원가입 토큰은 검증할 수 없다")
    void verify_missingTokenId_throwsUnauthorizedException() {
        // Given
        JwtSocialSignupTokenProvider provider = createProvider(Clock.fixed(
                NOW,
                ZoneOffset.UTC));
        // createSignedToken은 실제 발급 경로와 달리 jti를 채우지 않는다.
        String token = createSignedToken("SOCIAL_SIGNUP", true);

        // When & Then
        assertInvalidSignupToken(() -> provider.verify(token));
    }

    @Test
    @DisplayName("업로드 식별자가 없는 회원가입 토큰은 검증할 수 없다")
    void verify_missingUploadId_throwsUnauthorizedException() {
        // Given
        JwtSocialSignupTokenProvider provider = createProvider(Clock.fixed(
                NOW,
                ZoneOffset.UTC));
        String token = createSignedToken("SOCIAL_SIGNUP", false);

        // When & Then
        assertInvalidSignupToken(() -> provider.verify(token));
    }

    @Test
    @DisplayName("발급 시각이 없는 회원가입 토큰은 검증할 수 없다")
    void verify_missingIssuedAt_throwsUnauthorizedException() {
        // Given
        JwtSocialSignupTokenProvider provider = createProvider(Clock.fixed(
                NOW,
                ZoneOffset.UTC));
        String token = createSignedToken(
                "SOCIAL_SIGNUP",
                true,
                null,
                NOW.plus(Duration.ofMinutes(30)));

        // When & Then
        assertInvalidSignupToken(() -> provider.verify(token));
    }

    @Test
    @DisplayName("만료 시각이 없는 회원가입 토큰은 검증할 수 없다")
    void verify_missingExpiresAt_throwsUnauthorizedException() {
        // Given
        JwtSocialSignupTokenProvider provider = createProvider(Clock.fixed(
                NOW,
                ZoneOffset.UTC));
        String token = createSignedToken(
                "SOCIAL_SIGNUP",
                true,
                NOW,
                null);

        // When & Then
        assertInvalidSignupToken(() -> provider.verify(token));
    }

    @Test
    @DisplayName("허용 오차보다 미래에 발급된 회원가입 토큰은 검증할 수 없다")
    void verify_issuedAtBeyondClockSkew_throwsUnauthorizedException() {
        // Given
        JwtSocialSignupTokenProvider provider = createProvider(Clock.fixed(
                NOW,
                ZoneOffset.UTC));
        String token = createSignedToken(
                "SOCIAL_SIGNUP",
                true,
                NOW.plusSeconds(31),
                NOW.plus(Duration.ofMinutes(30)));

        // When & Then
        assertInvalidSignupToken(() -> provider.verify(token));
    }

    private JwtSocialSignupTokenProvider createProvider(Clock clock) {
        return createProvider(
                clock,
                "chalkak-backend",
                "chalkak-social-signup",
                SECRET);
    }

    private JwtSocialSignupTokenProvider createProvider(
            Clock clock,
            String issuer,
            String audience,
            String secret
    ) {
        return new JwtSocialSignupTokenProvider(
                new SocialSignupTokenProperties(
                        issuer,
                        audience,
                        Duration.ofMinutes(5),
                        secret),
                clock);
    }

    private VerifiedSocialIdentity identity() {
        return new VerifiedSocialIdentity(
                SocialProvider.GOOGLE,
                SUBJECT,
                "user@chalkak.test");
    }

    private String createSignedToken(String purpose, boolean includeUploadId) {
        return createSignedToken(
                purpose,
                includeUploadId,
                NOW,
                NOW.plus(Duration.ofMinutes(30)));
    }

    private String createSignedToken(
            String purpose,
            boolean includeUploadId,
            Instant issuedAt,
            Instant expiresAt
    ) {
        return createSignedToken(
                purpose,
                includeUploadId,
                issuedAt,
                expiresAt,
                SocialProvider.GOOGLE);
    }

    private String createSignedToken(
            String purpose,
            boolean includeUploadId,
            Instant issuedAt,
            Instant expiresAt,
            SocialProvider provider
    ) {
        SecretKey secretKey = new SecretKeySpec(
                HexFormat.of().parseHex(SECRET),
                "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(
                new ImmutableSecret<SecurityContext>(secretKey));
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("chalkak-backend")
                .audience(List.of("chalkak-social-signup"))
                .subject(SUBJECT)
                .claim("purpose", purpose)
                .claim("provider", provider.name());
        if (issuedAt != null) {
            claims.issuedAt(issuedAt);
        }
        if (expiresAt != null) {
            claims.expiresAt(expiresAt);
        }
        if (includeUploadId) {
            claims.claim("uploadId", UUID.randomUUID().toString());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build()))
                .getTokenValue();
    }

    private void assertInvalidSignupToken(ThrowingCallable callable) {
        assertThatThrownBy(callable::call)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 회원가입 정보입니다.");
    }

    @FunctionalInterface
    private interface ThrowingCallable {

        void call();
    }
}
