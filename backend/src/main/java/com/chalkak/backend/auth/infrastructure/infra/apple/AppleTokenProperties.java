package com.chalkak.backend.auth.infrastructure.infra.apple;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("chalkak.auth.apple.token")
public record AppleTokenProperties(
        @NotBlank String teamId,
        @NotBlank String keyId,
        @NotBlank String privateKeyBase64,
        @NotBlank String tokenUri,
        @NotBlank String revokeUri
) {
}
