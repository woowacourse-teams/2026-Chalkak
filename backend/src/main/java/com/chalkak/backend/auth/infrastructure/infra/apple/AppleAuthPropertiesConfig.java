package com.chalkak.backend.auth.infrastructure.infra.apple;

import com.chalkak.backend.auth.infrastructure.infra.oidc.apple.AppleOidcProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        AppleOidcProperties.class,
        AppleTokenProperties.class,
        AppleRefreshTokenEncryptionProperties.class
})
public class AppleAuthPropertiesConfig {
}
