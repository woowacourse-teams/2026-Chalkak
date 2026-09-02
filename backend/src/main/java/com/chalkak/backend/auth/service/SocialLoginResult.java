package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.IssuedAccessToken;
import java.util.UUID;

public record SocialLoginResult(
        SocialLoginStatus status,
        UUID userId,
        IssuedAccessToken accessToken
) {

    public static SocialLoginResult loginSuccess(
            UUID userId,
            IssuedAccessToken accessToken
    ) {
        return new SocialLoginResult(
                SocialLoginStatus.LOGIN_SUCCESS,
                userId,
                accessToken);
    }

    public static SocialLoginResult signUpRequired() {
        return new SocialLoginResult(
                SocialLoginStatus.SIGN_UP_REQUIRED,
                null,
                null);
    }
}
