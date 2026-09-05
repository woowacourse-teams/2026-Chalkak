package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.IssuedAccessToken;
import com.chalkak.backend.auth.domain.IssuedRefreshToken;

/** 재발급 한 번의 결과. 액세스 토큰과 회전된 리프레시 토큰은 항상 짝으로 내려간다. */
public record TokenRefreshResult(
        IssuedAccessToken accessToken,
        IssuedRefreshToken refreshToken
) {
}
