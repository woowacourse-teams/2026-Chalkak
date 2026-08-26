package com.chalkak.backend.auth.infrastructure.infra;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

final class GoogleIdTokenAudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String clientId;

    GoogleIdTokenAudienceValidator(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        boolean hasSingleAllowedAudience = jwt.getAudience().size() == 1
                && jwt.getAudience().contains(clientId);
        if (hasSingleAllowedAudience) {
            return OAuth2TokenValidatorResult.success();
        }
        OAuth2Error error = new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN,
                "Google ID Token audience가 허용되지 않았습니다.",
                null);
        return OAuth2TokenValidatorResult.failure(error);
    }
}
