package com.chalkak.backend.auth.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.auth.domain.AccessTokenScope;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
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
     * 어떤 scope를 가진 토큰인지는 필터 체인이 이미 판단했다. 여기서 다시 보면 같은 규칙이 두 곳으로 흩어지므로, 이 클래스는 주체에서
     * 회원 식별자를 꺼내는 일만 한다.
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
        assertThat(authenticatedUser).contains(new AuthenticatedUser(userId, null));
    }

    @Test
    @DisplayName("검증된 JWT의 로그인 ID를 UUID로 꺼낸다")
    void find_sessionClaim_returnsSessionId() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        authenticate(userId, sessionId.toString().toUpperCase());

        // When & Then
        assertThat(AuthenticatedUsers.find())
                .contains(new AuthenticatedUser(userId, sessionId));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "invalid", "1-1-1-1-1"})
    @DisplayName("로그인 ID가 없거나 형식이 잘못되어도 기존 API에 회원 식별자를 제공한다")
    void find_invalidSessionClaim_preservesUserIdentity(String sessionClaim) {
        // Given
        UUID userId = UUID.randomUUID();
        authenticate(userId, sessionClaim);

        // When & Then
        assertThat(AuthenticatedUsers.find()).contains(new AuthenticatedUser(userId, null));
    }

    @Test
    @DisplayName("로그인 ID가 문자열이 아니면 로그인 ID가 없는 것으로 처리한다")
    void find_nonStringSessionClaim_returnsNoSessionId() {
        // Given
        UUID userId = UUID.randomUUID();
        authenticate(userId, 123);

        // When & Then
        assertThat(AuthenticatedUsers.find()).contains(new AuthenticatedUser(userId, null));
    }

    private void authenticate(UUID userId, Object sessionClaim) {
        Jwt.Builder builder = Jwt.withTokenValue("verified-access-token")
                .header("alg", "HS256")
                .subject(userId.toString());
        if (sessionClaim != null) {
            builder.claim("session_id", sessionClaim);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                builder.build(),
                List.of(new SimpleGrantedAuthority(AccessTokenScope.USER.toAuthority()))));
    }
}
