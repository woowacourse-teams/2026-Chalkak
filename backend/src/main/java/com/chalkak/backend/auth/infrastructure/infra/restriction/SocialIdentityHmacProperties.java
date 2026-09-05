package com.chalkak.backend.auth.infrastructure.infra.restriction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("chalkak.auth.social-identity-hmac")
public record SocialIdentityHmacProperties(
        @NotBlank
        @Pattern(regexp = "[0-9a-fA-F]{64}") String secret
) {
}
