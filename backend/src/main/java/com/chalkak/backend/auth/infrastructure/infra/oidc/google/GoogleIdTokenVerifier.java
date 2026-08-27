package com.chalkak.backend.auth.infrastructure.infra.oidc.google;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.service.IdTokenVerifier;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

public class GoogleIdTokenVerifier implements IdTokenVerifier {

    private static final int SUBJECT_MAX_LENGTH = 255;

    private final JwtDecoder jwtDecoder;

    public GoogleIdTokenVerifier(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public SocialProvider getProvider() {
        return SocialProvider.GOOGLE;
    }

    @Override
    public VerifiedSocialIdentity verify(String idToken) {
        Jwt jwt = decode(idToken);
        return new VerifiedSocialIdentity(
                SocialProvider.GOOGLE,
                getSubject(jwt),
                getVerifiedEmail(jwt));
    }

    private Jwt decode(String idToken) {
        try {
            return jwtDecoder.decode(idToken);
        } catch (JwtException exception) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    "유효하지 않은 Google ID Token입니다.");
        }
    }

    private String getVerifiedEmail(Jwt jwt) {
        if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            return null;
        }
        return jwt.getClaimAsString("email");
    }

    private String getSubject(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    "Google ID Token에 사용자 식별 정보가 없습니다.");
        }
        if (subject.length() > SUBJECT_MAX_LENGTH) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    "Google ID Token의 사용자 식별 정보가 너무 깁니다.");
        }
        return subject;
    }
}
