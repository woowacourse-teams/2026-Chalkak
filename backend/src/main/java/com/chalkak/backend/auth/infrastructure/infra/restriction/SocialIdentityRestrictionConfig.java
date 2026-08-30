package com.chalkak.backend.auth.infrastructure.infra.restriction;

import com.chalkak.backend.auth.service.SocialIdentityFingerprintEncoder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SocialIdentityHmacProperties.class)
public class SocialIdentityRestrictionConfig {

    @Bean
    public SocialIdentityFingerprintEncoder socialIdentityFingerprintEncoder(
            SocialIdentityHmacProperties properties
    ) {
        return new HmacSocialIdentityFingerprintEncoder(properties.secret());
    }
}
