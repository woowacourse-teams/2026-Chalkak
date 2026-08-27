package com.chalkak.backend.auth.infrastructure.infra;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.service.IdTokenVerifier;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

public class KakaoIdTokenVerifier implements IdTokenVerifier {

    private static final int SUBJECT_MAX_LENGTH = 255;

    private final JwtDecoder jwtDecoder;

    public KakaoIdTokenVerifier(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public SocialProvider getProvider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public VerifiedSocialIdentity verify(String idToken) {
        Jwt jwt = decode(idToken);
        return new VerifiedSocialIdentity(
                SocialProvider.KAKAO,
                getSubject(jwt),
                jwt.getClaimAsString("email"));
    }

    private Jwt decode(String idToken) {
        try {
            return jwtDecoder.decode(idToken);
        } catch (JwtException exception) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    "유효하지 않은 Kakao ID Token입니다.");
        }
    }

    private String getSubject(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    "Kakao ID Token에 사용자 식별 정보가 없습니다.");
        }
        if (subject.length() > SUBJECT_MAX_LENGTH) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    "Kakao ID Token의 사용자 식별 정보가 너무 깁니다.");
        }
        return subject;
    }
}
