package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.IssuedAccessToken;
import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import java.util.UUID;

public record SocialSignupResult(
        UUID userId,
        IssuedAccessToken accessToken,
        IssuedRefreshToken refreshToken
) {
}
