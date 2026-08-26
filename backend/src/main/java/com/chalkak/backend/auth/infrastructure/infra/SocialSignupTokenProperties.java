package com.chalkak.backend.auth.infrastructure.infra;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("chalkak.auth.social-signup-token")
public record SocialSignupTokenProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration expiration,
        @NotBlank
        @Pattern(regexp = "[0-9a-fA-F]{64}") String secret
) {

    public SocialSignupTokenProperties {
        if (expiration != null && !expiration.isPositive()) {
            throw new IllegalArgumentException(
                    "Social signup token expiration must be positive");
        }
    }
}
