package com.chalkak.backend.auth.infrastructure.infra.oidc;

import com.chalkak.backend.auth.domain.SocialProvider;
import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * ID Token이 우리 앱에만 발급됐는지 확인한다. aud에 다른 앱이 함께 들어 있으면 그 앱도 같은 토큰을 받을 수 있으므로, 허용한
 * 값 하나만 들어 있을 때 통과시킨다. aud가 없는 토큰도 대상 앱을 알 수 없으므로 거절한다.
 */
final class OidcIdTokenAudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final SocialProvider provider;
    private final String audience;

    OidcIdTokenAudienceValidator(SocialProvider provider, String audience) {
        this.provider = provider;
        this.audience = audience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (hasSingleAllowedAudience(jwt.getAudience())) {
            return OAuth2TokenValidatorResult.success();
        }
        OAuth2Error error = new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN,
                provider.name() + " ID Token audience가 허용되지 않았습니다.",
                null);
        return OAuth2TokenValidatorResult.failure(error);
    }

    private boolean hasSingleAllowedAudience(List<String> audiences) {
        if (audiences == null) {
            return false;
        }
        return audiences.size() == 1 && audiences.contains(audience);
    }
}
