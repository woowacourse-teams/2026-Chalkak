package com.chalkak.backend.support;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

public class WithMockLoginUserSecurityContextFactory
        implements WithSecurityContextFactory<WithMockLoginUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockLoginUser annotation) {
        String userId = annotation.value();
        if (userId.isBlank()) {
            userId = UUID.randomUUID().toString();
        }

        Instant issuedAt = Instant.now();
        Jwt jwt = Jwt.withTokenValue("mock-access-token")
                .header("alg", "HS256")
                .subject(userId)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .claim("purpose", "ACCESS")
                .build();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        return context;
    }
}
