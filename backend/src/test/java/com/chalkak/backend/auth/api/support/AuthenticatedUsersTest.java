package com.chalkak.backend.auth.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.auth.domain.AccessTokenScope;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthenticatedUsersTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 어떤 scope를 가진 토큰인지는 필터 체인이 이미 판단했다. 여기서 다시 보면 같은 규칙이
     * 두 곳으로 흩어지므로, 이 클래스는 주체에서 회원 식별자를 꺼내는 일만 한다.
     */
    @Test
    @DisplayName("검증된 회원 JWT에서 회원 식별자를 꺼낸다")
    void find_jwtPrincipal_returnsAuthenticatedUser() {
        // Given
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("verified-access-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("scope", AccessTokenScope.USER.name())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority(AccessTokenScope.USER.toAuthority()))));

        // When
        Optional<AuthenticatedUser> authenticatedUser = AuthenticatedUsers.find();

        // Then
        assertThat(authenticatedUser).contains(new AuthenticatedUser(userId));
    }
}
