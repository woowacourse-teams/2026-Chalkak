package com.chalkak.backend.auth.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthenticatedUsersTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("기존 scope 없는 회원 JWT도 검증된 회원 식별자로 사용할 수 있다")
    void find_legacyTokenWithoutScope_returnsAuthenticatedUser() {
        // Given
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("verified-legacy-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of()));

        // When
        Optional<AuthenticatedUser> authenticatedUser = AuthenticatedUsers.find();

        // Then
        assertThat(authenticatedUser).contains(new AuthenticatedUser(userId));
    }
}
