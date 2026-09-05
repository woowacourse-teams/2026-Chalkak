package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.IssuedAccessToken;
import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import java.util.UUID;

/**
 * 기존 회원 로그인이 성공했을 때의 결과만 표현한다. 회원가입이 필요한 경우는 제공자마다 이어지는 절차가
 * 달라 상위 로그인 서비스가 판단하므로 여기서 표현하지 않는다.
 */
public record SocialLoginSuccess(
        UUID userId,
        IssuedAccessToken accessToken,
        IssuedRefreshToken refreshToken
) {
}
