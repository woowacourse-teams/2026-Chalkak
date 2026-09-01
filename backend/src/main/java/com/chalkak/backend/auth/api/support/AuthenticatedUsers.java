package com.chalkak.backend.auth.api.support;

import com.chalkak.backend.auth.domain.AccessTokenScope;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 검증이 끝난 액세스 토큰에서 로그인 사용자를 꺼낸다. Security 필터와 디코더가 서명과
 * {@code sub}의 UUID 형식을 검증하며, 여기서는 관리자 식별자가 회원으로 사용되지 않게 한다.
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
        validateNotAdmin(authentication);
        return Optional.of(new AuthenticatedUser(UUID.fromString(jwt.getSubject())));
    }

    /**
     * 기존 scope 없는 회원 토큰은 유지하되 관리자 식별자를 회원 식별자로 사용하지 않는다.
     * 회원 저장소를 읽는 지점보다 먼저 걸러야 관리자 토큰이 없는 회원으로 접히지 않는다.
     */
    static void validateNotAdmin(Authentication authentication) {
        if (authentication.getAuthorities().stream()
                .anyMatch(authority -> AccessTokenScope.ADMIN.toAuthority()
                        .equals(authority.getAuthority()))) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "일반 사용자 권한이 필요합니다.");
        }
    }
}
