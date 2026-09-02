package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.IssuedAccessToken;
import java.util.UUID;

public record SocialSignupResult(
        UUID userId,
        IssuedAccessToken accessToken
) {
}
