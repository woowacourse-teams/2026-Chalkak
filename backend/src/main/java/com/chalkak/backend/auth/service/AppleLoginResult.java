package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.IssuedAccessToken;
import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import java.util.UUID;

public record AppleLoginResult(
        SocialLoginStatus status,
        UUID userId,
        IssuedAccessToken accessToken,
        IssuedRefreshToken refreshToken
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
                refreshToken);
    }

    /**
     * 회원가입 토큰은 업로드 URL 발급이 내준다. 로그인은 가입이 필요하다는 사실만 알리고, 교환한
     * Refresh Token은 신원으로 임시 보관해 다음 단계가 찾아 쓴다.
     */
    public static AppleLoginResult signUpRequired() {
        return new AppleLoginResult(
                SocialLoginStatus.SIGN_UP_REQUIRED,
                null,
                null,
                null);
    }
}
