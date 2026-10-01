package com.chalkak.backend.auth.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.auth.domain.AccessTokenScope;
import com.chalkak.backend.common.logging.LogFields;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AccessLogUserInterceptorTest {

    private final AccessLogUserInterceptor interceptor = new AccessLogUserInterceptor();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("회원 토큰이면 회원 식별자를 요청 속성에 담는다")
    void preHandle_memberToken_setsUserIdAttribute() {
        // Given
        UUID userId = UUID.randomUUID();
        authenticate(userId, AccessTokenScope.USER);
        MockHttpServletRequest request = new MockHttpServletRequest();

        // When
        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        // Then
        assertThat(request.getAttribute(LogFields.USER_ID_REQUEST_ATTRIBUTE))
                .isEqualTo(userId.toString());
    }

    @Test
    @DisplayName("관리자 토큰이면 요청 속성에 아무것도 담지 않는다")
    void preHandle_adminToken_doesNotSetUserIdAttribute() {
        // Given
        authenticate(UUID.randomUUID(), AccessTokenScope.ADMIN);
        MockHttpServletRequest request = new MockHttpServletRequest();

        // When
        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        // Then
        assertThat(request.getAttribute(LogFields.USER_ID_REQUEST_ATTRIBUTE)).isNull();
    }

    private void authenticate(UUID subject, AccessTokenScope scope) {
        Instant issuedAt = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject.toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(60))
                .build();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority(scope.toAuthority()))));
        SecurityContextHolder.setContext(context);
    }
}
