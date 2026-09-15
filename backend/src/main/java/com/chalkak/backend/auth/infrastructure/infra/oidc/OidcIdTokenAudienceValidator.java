package com.chalkak.backend.auth.infrastructure.infra.oidc;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * ID Token이 우리 앱에만 발급됐는지 확인한다. aud에 다른 앱이 함께 들어 있으면 그 앱도 같은 토큰을 받을 수 있으므로, 허용한
 * 값 하나만 들어 있을 때 통과시킨다.
 */
final class OidcIdTokenAudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String providerName;
    private final String audience;

    OidcIdTokenAudienceValidator(String providerName, String audience) {
        this.providerName = providerName;
        this.audience = audience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        boolean hasSingleAllowedAudience = jwt.getAudience().size() == 1
                && jwt.getAudience().contains(audience);
        if (hasSingleAllowedAudience) {
            return OAuth2TokenValidatorResult.success();
        }
        OAuth2Error error = new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN,
                providerName + " ID Token audience가 허용되지 않았습니다.",
                null);
        return OAuth2TokenValidatorResult.failure(error);
    }
}
