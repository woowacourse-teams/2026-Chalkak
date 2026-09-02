package com.chalkak.backend.auth.infrastructure.infra.access;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AccessTokenProperties.class)
public class AccessTokenConfig {

    @Bean
    public JwtAccessTokenProvider accessTokenProvider(
            AccessTokenProperties properties
    ) {
        return new JwtAccessTokenProvider(properties);
    }
}
