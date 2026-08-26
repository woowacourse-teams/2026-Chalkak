package com.chalkak.backend.auth.infrastructure.infra;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SocialSignupTokenProperties.class)
public class SocialSignupTokenConfig {

    @Bean
    public JwtSocialSignupTokenProvider socialSignupTokenProvider(
            SocialSignupTokenProperties properties
    ) {
        return new JwtSocialSignupTokenProvider(properties);
    }
}
