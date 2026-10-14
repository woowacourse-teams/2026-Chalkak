package com.chalkak.backend.admin.service.auth;

import com.chalkak.backend.auth.domain.token.IssuedAccessToken;
import com.chalkak.backend.auth.domain.token.IssuedRefreshToken;
import java.util.UUID;

public record AdminLoginResult(
        UUID adminId,
        String username,
        IssuedAccessToken accessToken,
        IssuedRefreshToken refreshToken) {
}
