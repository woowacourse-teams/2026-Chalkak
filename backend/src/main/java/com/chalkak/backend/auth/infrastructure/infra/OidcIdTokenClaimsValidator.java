package com.chalkak.backend.auth.infrastructure.infra;

import java.time.Clock;
import java.time.Duration;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

final class OidcIdTokenClaimsValidator implements OAuth2TokenValidator<Jwt> {

    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    private final Clock clock;

    OidcIdTokenClaimsValidator() {
        this(Clock.systemUTC());
    }

    OidcIdTokenClaimsValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (jwt.getExpiresAt() == null) {
            return failure("ID Token exp claim이 없습니다.");
        }
        if (jwt.getIssuedAt() == null) {
            return failure("ID Token iat claim이 없습니다.");
        }
        if (jwt.getIssuedAt().isAfter(clock.instant().plus(CLOCK_SKEW))) {
            return failure("ID Token iat claim이 허용 범위보다 미래입니다.");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private OAuth2TokenValidatorResult failure(String message) {
        OAuth2Error error = new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN,
                message,
                null);
        return OAuth2TokenValidatorResult.failure(error);
    }
}
