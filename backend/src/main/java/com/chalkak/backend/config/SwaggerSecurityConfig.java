package com.chalkak.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Swagger UI와 OpenAPI 문서만 여는 체인. prod에서는 이 설정 자체가 없으므로 운영의 인증 예외
 * 범위가 넓어지지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!prod")
public class SwaggerSecurityConfig {

    private static final String[] SPRINGDOC_PATHS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**"
    };

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain springdocSecurityFilterChain(HttpSecurity http)
            throws Exception {
        return http
                .securityMatcher(SPRINGDOC_PATHS)
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(request -> request.anyRequest().permitAll())
                .build();
    }
}
