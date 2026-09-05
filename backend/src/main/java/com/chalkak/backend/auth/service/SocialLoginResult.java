package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.IssuedAccessToken;
import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import java.util.UUID;

public record SocialLoginResult(
        SocialLoginStatus status,
        UUID userId,
        IssuedAccessToken accessToken,
        IssuedRefreshToken refreshToken
) {

    public static SocialLoginResult loginSuccess(
            UUID userId,
            IssuedAccessToken accessToken,
            IssuedRefreshToken refreshToken
    ) {
        return new SocialLoginResult(
                SocialLoginStatus.LOGIN_SUCCESS,
                userId,
                accessToken,
                refreshToken);
    }

    public static SocialLoginResult signUpRequired() {
        return new SocialLoginResult(
                SocialLoginStatus.SIGN_UP_REQUIRED,
                null,
                null,
                null);
    }
}
