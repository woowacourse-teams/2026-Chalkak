package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.SocialProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Apple 전용 로그인 엔드포인트가 남아 있는 동안만 공통 로그인으로 넘겨준다. 엔드포인트를 제거할
 * 때(#412) 이 클래스도 함께 사라진다.
 */
@Service
@RequiredArgsConstructor
public class AppleLoginService {

    private final SocialLoginService socialLoginService;

    public AppleLoginResult login(
            String idToken,
            String authorizationCode,
            String rawNonce
    ) {
        SocialLoginResult result = socialLoginService.login(
                SocialProvider.APPLE,
                idToken,
                rawNonce,
                authorizationCode);

        if (result.status() == SocialLoginStatus.LOGIN_SUCCESS) {
            return AppleLoginResult.loginSuccess(
                    result.userId(),
                    result.accessToken(),
                    result.refreshToken());
        }
        return AppleLoginResult.signUpRequired();
    }
}
