package com.chalkak.backend.auth.infrastructure.infra.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.auth.domain.AccessTokenScope;
import com.chalkak.backend.auth.domain.IssuedAccessToken;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtAccessTokenProviderTest {

    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");
    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String ISSUER = "chalkak-backend";
    private static final String AUDIENCE = "chalkak-access";
    private static final Duration EXPIRATION = Duration.ofMinutes(15);

    @Test
    @DisplayName("회원 식별자로 액세스 토큰을 발급하고 검증한다")
    void issue_validUserId_issuesVerifiableAccessToken() {
        // Given
        JwtAccessTokenProvider provider = createProvider(NOW);
        UUID userId = UUID.randomUUID();

        // When
        IssuedAccessToken issuedToken = provider.issue(userId);
        Jwt jwt = provider.jwtDecoder().decode(issuedToken.value());

        // Then
        assertThat(issuedToken.value()).isNotBlank();
        assertThat(issuedToken.expiresIn()).isEqualTo(EXPIRATION);
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("USER");
    }

    @Test
    @DisplayName("관리자 식별자로 관리자 scope의 액세스 토큰을 발급한다")
    void issue_adminScope_issuesAdminAccessToken() {
        // Given
        JwtAccessTokenProvider provider = createProvider(NOW);
        UUID adminId = UUID.randomUUID();

        // When
        IssuedAccessToken issuedToken = provider.issue(adminId, AccessTokenScope.ADMIN);
        Jwt jwt = provider.jwtDecoder().decode(issuedToken.value());

        // Then
        assertThat(jwt.getSubject()).isEqualTo(adminId.toString());
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("같은 비밀키로 서명됐어도 회원가입 토큰은 액세스 토큰으로 사용할 수 없다")
    void decode_socialSignupTokenSignedWithSameSecret_throwsJwtException() {
        // Given
        JwtAccessTokenProvider provider = createProvider(NOW);
        String signupToken = encodeWithSameSecret(JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of("chalkak-social-signup"))
                .subject("google-subject")
                .issuedAt(NOW)
                .expiresAt(NOW.plus(Duration.ofMinutes(5)))
                .claim("purpose", "SOCIAL_SIGNUP")
                .build());

        // When & Then
        assertThatThrownBy(() -> provider.jwtDecoder().decode(signupToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("purpose가 같아도 audience가 다른 토큰은 검증할 수 없다")
    void decode_audienceMismatch_throwsJwtException() {
        // Given
        JwtAccessTokenProvider provider = createProvider(NOW);
        String token = encodeWithSameSecret(claims()
                .audience(List.of("chalkak-other"))
                .build());

        // When & Then
        assertThatThrownBy(() -> provider.jwtDecoder().decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("audience가 같아도 purpose가 다른 토큰은 검증할 수 없다")
    void decode_purposeMismatch_throwsJwtException() {
        // Given
        JwtAccessTokenProvider provider = createProvider(NOW);
        String token = encodeWithSameSecret(claims()
                .claim("purpose", "SOCIAL_SIGNUP")
                .build());

        // When & Then
        assertThatThrownBy(() -> provider.jwtDecoder().decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("purpose 클레임이 없는 토큰은 검증할 수 없다")
    void decode_missingPurpose_throwsJwtException() {
        // Given
        JwtAccessTokenProvider provider = createProvider(NOW);
        String token = encodeWithSameSecret(JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(UUID.randomUUID().toString())
                .issuedAt(NOW)
                .expiresAt(NOW.plus(EXPIRATION))
                .build());

        // When & Then
        assertThatThrownBy(() -> provider.jwtDecoder().decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("발급자가 다른 토큰은 검증할 수 없다")
    void decode_issuerMismatch_throwsJwtException() {
        // Given
        JwtAccessTokenProvider provider = createProvider(NOW);
        String token = encodeWithSameSecret(claims()
                .issuer("attacker")
                .build());

        // When & Then
        assertThatThrownBy(() -> provider.jwtDecoder().decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("서명이 변조된 토큰은 검증할 수 없다")
    void decode_tamperedToken_throwsJwtException() {
        // Given
        JwtAccessTokenProvider provider = createProvider(NOW);
        String token = provider.issue(UUID.randomUUID()).value();
        String tamperedToken = tamperSignature(token);

        // When & Then
        assertThatThrownBy(() -> provider.jwtDecoder().decode(tamperedToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("만료 시각을 막 지난 토큰은 허용 오차 안에서 검증할 수 있다")
    void decode_justPastExpiryWithinClockSkew_verifiesAccessToken() {
        // Given
        String token = createProvider(NOW).issue(UUID.randomUUID()).value();
        JwtAccessTokenProvider verifier =
                createProvider(NOW.plus(EXPIRATION).plusSeconds(29));

        // When
        Jwt jwt = verifier.jwtDecoder().decode(token);

        // Then
        assertThat(jwt.getSubject()).isNotBlank();
    }

    @Test
    @DisplayName("허용 오차를 넘겨 만료된 토큰은 검증할 수 없다")
    void decode_expiredBeyondClockSkew_throwsJwtException() {
        // Given
        String token = createProvider(NOW).issue(UUID.randomUUID()).value();
        JwtAccessTokenProvider verifier =
                createProvider(NOW.plus(EXPIRATION).plusSeconds(31));

        // When & Then
        assertThatThrownBy(() -> verifier.jwtDecoder().decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("만료 시각이 없는 토큰은 검증할 수 없다")
    void decode_missingExpiry_throwsJwtException() {
        // Given
        JwtAccessTokenProvider provider = createProvider(NOW);
        String token = encodeWithSameSecret(JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(UUID.randomUUID().toString())
                .issuedAt(NOW)
                .claim("purpose", "ACCESS")
                .build());

        // When & Then
        assertThatThrownBy(() -> provider.jwtDecoder().decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("허용 오차와 정확히 같은 시각에 만료된 토큰은 검증할 수 있다")
    void decode_exactlyAtClockSkewBoundary_verifiesAccessToken() {
        // Given
        String token = createProvider(NOW).issue(UUID.randomUUID()).value();
        JwtAccessTokenProvider verifier =
                createProvider(NOW.plus(EXPIRATION).plusSeconds(30));

        // When
        Jwt jwt = verifier.jwtDecoder().decode(token);

        // Then
        assertThat(jwt.getSubject()).isNotBlank();
    }

    @Test
    @DisplayName("subject가 회원 식별자 형식이 아닌 토큰은 검증할 수 없다")
    void decode_nonUuidSubject_throwsJwtException() {
        // Given
        JwtAccessTokenProvider provider = createProvider(NOW);
        String token = encodeWithSameSecret(claims()
                .subject("google-subject")
                .build());

        // When & Then
        assertThatThrownBy(() -> provider.jwtDecoder().decode(token))
                .isInstanceOf(JwtException.class);
    }

    /**
     * 서명의 첫 글자를 바꾼다. base64url 마지막 글자는 32바이트 서명에서 유효 비트가 4개뿐이라
     * 글자를 바꿔도 디코딩된 서명 바이트가 그대로일 수 있다. 첫 글자는 6비트가 모두 유효하다.
     */
    private String tamperSignature(String token) {
        int signatureStart = token.lastIndexOf('.') + 1;
        char original = token.charAt(signatureStart);
        char replacement = original == 'A' ? 'B' : 'A';
        return token.substring(0, signatureStart)
                + replacement
                + token.substring(signatureStart + 1);
    }

    private JwtClaimsSet.Builder claims() {
        return JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(UUID.randomUUID().toString())
                .issuedAt(NOW)
                .expiresAt(NOW.plus(EXPIRATION))
                .claim("purpose", "ACCESS");
    }

    private String encodeWithSameSecret(JwtClaimsSet claims) {
        SecretKey secretKey = new SecretKeySpec(
                HexFormat.of().parseHex(SECRET),
                "HmacSHA256");
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(
                new ImmutableSecret<SecurityContext>(secretKey));
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    private JwtAccessTokenProvider createProvider(Instant now) {
        return new JwtAccessTokenProvider(
                new AccessTokenProperties(ISSUER, AUDIENCE, EXPIRATION, SECRET),
                Clock.fixed(now, ZoneOffset.UTC));
    }
}
