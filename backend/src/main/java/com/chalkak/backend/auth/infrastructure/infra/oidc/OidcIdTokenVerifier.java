package com.chalkak.backend.auth.infrastructure.infra.oidc;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.service.IdTokenVerifier;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * 제공자의 ID Token에서 소셜 식별 정보를 꺼낸다. 서명·발급자·aud·시각은 디코더가 검증하고, 여기서는 nonce와 사용자 식별
 * 정보를 확인한다.
 *
 * <p>
 * 클라이언트는 로그인 전에 원본 nonce를 만들어 SDK에는 SHA-256 소문자 hex를 넘기고, 서버에는 원본을 보낸다. 제공자는 받은
 * 해시를 ID Token의 {@code nonce}에 담아 서명하므로, 토큰만 탈취한 쪽은 그 해시를 만든 원본을 제시할 수 없다.
 */
final class OidcIdTokenVerifier implements IdTokenVerifier {

    private static final int SUBJECT_MAX_LENGTH = 255;
    private static final String NONCE_CLAIM = "nonce";

    private final SocialProvider provider;
    private final JwtDecoder jwtDecoder;
    private final OidcEmailPolicy emailPolicy;

    OidcIdTokenVerifier(
            SocialProvider provider,
            JwtDecoder jwtDecoder,
            OidcEmailPolicy emailPolicy
    ) {
        this.provider = provider;
        this.jwtDecoder = jwtDecoder;
        this.emailPolicy = emailPolicy;
    }

    @Override
    public SocialProvider getProvider() {
        return provider;
    }

    @Override
    public VerifiedSocialIdentity verify(String idToken, String rawNonce) {
        validateRawNonce(rawNonce);
        Jwt jwt = decode(idToken);
        verifyNonce(jwt, rawNonce);
        return new VerifiedSocialIdentity(
                provider,
                getSubject(jwt),
                emailPolicy.extractEmail(jwt));
    }

    private void validateRawNonce(String rawNonce) {
        if (rawNonce == null || rawNonce.isBlank()) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    provider.getDisplayName() + " 로그인 nonce가 필요합니다.");
        }
    }

    private Jwt decode(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw invalidIdToken();
        }
        try {
            return jwtDecoder.decode(idToken);
        } catch (JwtException exception) {
            throw invalidIdToken();
        }
    }

    private UnauthorizedException invalidIdToken() {
        return new UnauthorizedException(
                ErrorCode.UNAUTHORIZED,
                "유효하지 않은 " + provider.getDisplayName() + " ID Token입니다.");
    }

    private void verifyNonce(Jwt jwt, String rawNonce) {
        String nonce = jwt.getClaimAsString(NONCE_CLAIM);
        byte[] expectedNonce = hash(rawNonce).getBytes(StandardCharsets.US_ASCII);
        boolean matches = nonce != null && MessageDigest.isEqual(
                expectedNonce,
                nonce.getBytes(StandardCharsets.US_ASCII));
        if (!matches) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    provider.getDisplayName() + " ID Token nonce가 일치하지 않습니다.");
        }
    }

    private String hash(String rawNonce) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawNonce.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }

    private String getSubject(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    provider.getDisplayName() + " ID Token에 사용자 식별 정보가 없습니다.");
        }
        if (subject.length() > SUBJECT_MAX_LENGTH) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    provider.getDisplayName() + " ID Token의 사용자 식별 정보가 너무 깁니다.");
        }
        return subject;
    }
}
