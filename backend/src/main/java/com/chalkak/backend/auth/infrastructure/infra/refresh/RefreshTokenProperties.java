package com.chalkak.backend.auth.infrastructure.infra.refresh;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("chalkak.auth.refresh-token")
public record RefreshTokenProperties(
        @NotNull Duration inactivityExpiration,
        @NotNull Duration absoluteExpiration,
        @NotNull Duration reuseGrace
) {

    public RefreshTokenProperties {
        validatePositive(inactivityExpiration, "inactivity expiration");
        validatePositive(absoluteExpiration, "absolute expiration");
        validatePositive(reuseGrace, "reuse grace");
        if (inactivityExpiration != null
                && absoluteExpiration != null
                && inactivityExpiration.compareTo(absoluteExpiration) > 0) {
            throw new IllegalArgumentException(
                    "Refresh token inactivity expiration must not exceed absolute expiration");
        }
    }

    private static void validatePositive(Duration duration, String name) {
        if (duration != null && !duration.isPositive()) {
            throw new IllegalArgumentException(
                    "Refresh token %s must be positive".formatted(name));
        }
    }
}
