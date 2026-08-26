package com.chalkak.backend.auth.service;

import java.util.UUID;

public record SocialLoginResult(
        SocialLoginStatus status,
        UUID userId
) {

    public static SocialLoginResult loginSuccess(UUID userId) {
        return new SocialLoginResult(SocialLoginStatus.LOGIN_SUCCESS, userId);
    }

    public static SocialLoginResult signUpRequired() {
        return new SocialLoginResult(SocialLoginStatus.SIGN_UP_REQUIRED, null);
    }
}
