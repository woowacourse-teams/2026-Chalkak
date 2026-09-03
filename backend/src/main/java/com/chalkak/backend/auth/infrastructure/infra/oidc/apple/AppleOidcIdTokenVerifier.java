package com.chalkak.backend.auth.infrastructure.infra.oidc.apple;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.service.AppleIdTokenVerifier;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

public class AppleOidcIdTokenVerifier implements AppleIdTokenVerifier {

    private static final int SUBJECT_MAX_LENGTH = 255;

    private final JwtDecoder jwtDecoder;

    public AppleOidcIdTokenVerifier(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public VerifiedSocialIdentity verify(String idToken, String rawNonce) {
        validateRawNonce(rawNonce);
        Jwt jwt = decode(idToken);
        verifyNonce(jwt, rawNonce);
        return new VerifiedSocialIdentity(
                SocialProvider.APPLE,
                getSubject(jwt),
                getVerifiedEmail(jwt));
    }

    private void validateRawNonce(String rawNonce) {
        if (rawNonce == null || rawNonce.isBlank()) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    "Apple 로그인 nonce가 필요합니다.");
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
                "유효하지 않은 Apple ID Token입니다.");
    }

    private void verifyNonce(Jwt jwt, String rawNonce) {
        String nonce = jwt.getClaimAsString("nonce");
        String expectedNonce = hash(rawNonce);
        boolean matches = nonce != null && MessageDigest.isEqual(
                expectedNonce.getBytes(StandardCharsets.US_ASCII),
                nonce.getBytes(StandardCharsets.US_ASCII));
        if (!matches) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    "Apple ID Token nonce가 일치하지 않습니다.");
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
                    "Apple ID Token에 사용자 식별 정보가 없습니다.");
        }
        if (subject.length() > SUBJECT_MAX_LENGTH) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    "Apple ID Token의 사용자 식별 정보가 너무 깁니다.");
        }
        return subject;
    }

    private String getVerifiedEmail(Jwt jwt) {
        Object emailVerified = jwt.getClaim("email_verified");
        boolean isVerified = Boolean.TRUE.equals(emailVerified)
                || "true".equalsIgnoreCase(String.valueOf(emailVerified));
        if (!isVerified) {
            return null;
        }
        return jwt.getClaimAsString("email");
    }
}
