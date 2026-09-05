package com.chalkak.backend.auth.infrastructure.infra.oidc.apple;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("chalkak.auth.oidc.apple")
public record AppleOidcProperties(
        @NotBlank String issuer,
        @NotBlank String jwkSetUri,
        @NotBlank String clientId
) {
}
