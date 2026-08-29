package com.chalkak.backend.admin.infrastructure.infra;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.auth.domain.AccessTokenScope;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.exception.UnauthorizedException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "chalkak.admin.authentication",
        name = "development-bypass-enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class SecurityContextAdminActorResolver implements AdminActorResolver {

    @Override
    public AuthenticatedAdmin resolve() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    "유효하지 않은 인증 정보입니다."
            );
        }
        boolean adminAuthority = authentication.getAuthorities().stream()
                .anyMatch(authority -> AccessTokenScope.ADMIN.authority()
                        .equals(authority.getAuthority()));
        if (!adminAuthority) {
            throw new ForbiddenException(
                    ErrorCode.FORBIDDEN,
                    "관리자 권한이 필요합니다."
            );
        }
        return new AuthenticatedAdmin(UUID.fromString(jwt.getSubject()));
    }
}
