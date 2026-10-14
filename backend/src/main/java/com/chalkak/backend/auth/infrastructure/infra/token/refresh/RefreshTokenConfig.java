package com.chalkak.backend.auth.infrastructure.infra.token.refresh;

import com.chalkak.backend.auth.domain.token.RefreshTokenPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RefreshTokenProperties.class)
public class RefreshTokenConfig {

    @Bean
    public SecureRandomRefreshTokenProvider refreshTokenProvider() {
        return new SecureRandomRefreshTokenProvider();
    }

    @Bean
    public RefreshTokenPolicy refreshTokenPolicy(RefreshTokenProperties properties) {
        return new RefreshTokenPolicy(
                properties.inactivityExpiration(),
                properties.absoluteExpiration());
    }
}
