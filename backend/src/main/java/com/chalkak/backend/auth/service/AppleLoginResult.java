package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.IssuedAccessToken;
import com.chalkak.backend.auth.domain.IssuedSocialSignupToken;
import java.util.UUID;

public record AppleLoginResult(
        SocialLoginStatus status,
        UUID userId,
        IssuedAccessToken accessToken,
        IssuedSocialSignupToken signupToken
) {

    public static AppleLoginResult loginSuccess(
            UUID userId,
            IssuedAccessToken accessToken
    ) {
        return new AppleLoginResult(
                SocialLoginStatus.LOGIN_SUCCESS,
                userId,
                accessToken,
                null);
    }

    public static AppleLoginResult signUpRequired(
            IssuedSocialSignupToken signupToken
    ) {
        return new AppleLoginResult(
                SocialLoginStatus.SIGN_UP_REQUIRED,
                null,
                null,
                signupToken);
    }
}
