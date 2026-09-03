package com.chalkak.backend.auth.infrastructure.infra.apple;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        AppleTokenProperties.class,
        AppleRefreshTokenEncryptionProperties.class
})
public class AppleAuthPropertiesConfig {
}
