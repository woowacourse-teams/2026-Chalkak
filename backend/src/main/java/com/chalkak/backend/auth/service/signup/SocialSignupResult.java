package com.chalkak.backend.auth.service.signup;

import com.chalkak.backend.auth.domain.token.IssuedAccessToken;
import com.chalkak.backend.auth.domain.token.IssuedRefreshToken;
import java.util.UUID;

public record SocialSignupResult(
        UUID userId,
        IssuedAccessToken accessToken,
        IssuedRefreshToken refreshToken) {
}
