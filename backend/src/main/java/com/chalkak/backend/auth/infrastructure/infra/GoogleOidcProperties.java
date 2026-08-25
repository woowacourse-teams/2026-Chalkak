package com.chalkak.backend.auth.infrastructure.infra;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("chalkak.auth.oidc.google")
public record GoogleOidcProperties(
        @NotBlank String issuer,
        @NotBlank String jwkSetUri,
        @NotBlank String clientId
) {
}
