package com.chalkak.backend.auth.api.support;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 검증이 끝난 액세스 토큰에서 로그인 사용자를 꺼낸다. 토큰 검증은 이미 Security 필터가 마쳤고
 * {@code sub}가 회원 식별자임도 디코더가 보장하므로, 여기서는 형식을 다시 의심하지 않는다.
 */
final class AuthenticatedUsers {

    private AuthenticatedUsers() {
    }

    static Optional<AuthenticatedUser> find() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        return Optional.of(new AuthenticatedUser(UUID.fromString(jwt.getSubject())));
    }
}
