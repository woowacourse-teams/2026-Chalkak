package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.IssuedAccessToken;
import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import com.chalkak.backend.auth.domain.IssuedSocialSignupToken;
import java.util.UUID;

public record AppleLoginResult(
        SocialLoginStatus status,
        UUID userId,
        IssuedAccessToken accessToken,
        IssuedRefreshToken refreshToken,
        IssuedSocialSignupToken signupToken
) {

    public static AppleLoginResult loginSuccess(
            UUID userId,
            IssuedAccessToken accessToken,
            IssuedRefreshToken refreshToken
    ) {
        return new AppleLoginResult(
                SocialLoginStatus.LOGIN_SUCCESS,
                userId,
                accessToken,
                refreshToken,
                null);
    }

    public static AppleLoginResult signUpRequired(
            IssuedSocialSignupToken signupToken
    ) {
        return new AppleLoginResult(
                SocialLoginStatus.SIGN_UP_REQUIRED,
                null,
                null,
                null,
                signupToken);
    }
}
