package com.chalkak.backend.admin.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.auth.domain.AccessTokenScope;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.exception.UnauthorizedException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SecurityContextAdminActorResolverTest {

    private final SecurityContextAdminActorResolver resolver =
            new SecurityContextAdminActorResolver();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("관리자 권한 JWT의 subject를 현재 관리자 식별자로 반환한다")
    void resolve_adminJwt_returnsAuthenticatedAdmin() {
        // Given
        UUID adminId = UUID.randomUUID();
        authenticate(adminId, AccessTokenScope.ADMIN);

        // When
        AuthenticatedAdmin authenticatedAdmin = resolver.resolve();

        // Then
        assertThat(authenticatedAdmin.adminId()).isEqualTo(adminId);
    }

    @Test
    @DisplayName("인증 정보가 없으면 401 예외를 반환한다")
    void resolve_withoutAuthentication_throwsUnauthorized() {
        assertThatThrownBy(resolver::resolve)
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("일반 사용자 권한 JWT이면 403 예외를 반환한다")
    void resolve_userJwt_throwsForbidden() {
        // Given
        authenticate(UUID.randomUUID(), AccessTokenScope.USER);

        // When & Then
        assertThatThrownBy(resolver::resolve)
                .isInstanceOf(ForbiddenException.class);
    }

    private void authenticate(UUID subjectId, AccessTokenScope scope) {
        Instant now = Instant.parse("2026-08-29T00:00:00Z");
        Jwt jwt = new Jwt(
                "token",
                now,
                now.plusSeconds(3600),
                Map.of("alg", "HS256"),
                Map.of("sub", subjectId.toString(), "scope", scope.name())
        );
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority(scope.toAuthority()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
