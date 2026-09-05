package com.chalkak.backend.admin.infrastructure.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("chalkak.admin.authentication")
public record AdminAuthenticationProperties(
        boolean developmentBypassEnabled
) {
}
