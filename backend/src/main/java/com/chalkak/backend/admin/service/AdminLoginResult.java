package com.chalkak.backend.admin.service;

import com.chalkak.backend.auth.domain.IssuedAccessToken;
import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import java.util.UUID;

public record AdminLoginResult(
        UUID adminId,
        String username,
        IssuedAccessToken accessToken,
        IssuedRefreshToken refreshToken
) {
}
