package com.chalkak.backend.auth.api.support;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 검증이 끝난 액세스 토큰에서 로그인 사용자를 꺼낸다. Security 필터와 디코더가 서명과
 * {@code sub}의 UUID 형식을 검증하므로, 여기서는 주체를 꺼내는 일만 한다.
 *
 * <p>관리자 식별자가 회원 식별자로 쓰이지 않게 막는 판단은
 * {@link com.chalkak.backend.config.SecurityConfig}의 경로 인가 규칙으로 옮겼다. 회원 API는
 * {@code SCOPE_USER}를 요구하므로 여기까지 도달한 주체는 이미 회원 토큰이다.
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
