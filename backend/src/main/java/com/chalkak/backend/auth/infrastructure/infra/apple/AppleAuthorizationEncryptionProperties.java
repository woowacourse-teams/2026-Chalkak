package com.chalkak.backend.auth.infrastructure.infra.apple;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("chalkak.auth.apple.refresh-token-encryption")
public record AppleAuthorizationEncryptionProperties(
        @NotBlank @Pattern(regexp = "[0-9A-Fa-f]{64}") String key
) {
}
