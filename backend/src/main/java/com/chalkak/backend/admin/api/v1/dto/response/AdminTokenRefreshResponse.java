package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.auth.domain.IssuedAccessToken;
import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import com.chalkak.backend.auth.service.TokenRefreshResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdminTokenRefreshResponse(
        @Schema(description = "재발급된 관리자 액세스 토큰")
        String accessToken,

        @Schema(
                description = "발급 시점부터의 액세스 토큰 유효 시간(초)",
                example = "900"
        )
        long expiresIn,

        @Schema(
                description = "회전으로 새로 발급된 리프레시 토큰."
                        + " 요청에 사용한 토큰은 더 이상 쓸 수 없으므로 이 값으로 교체한다"
        )
        String refreshToken,

        @Schema(
                description = "발급 시점부터의 리프레시 토큰 유효 시간(초)",
                example = "2592000"
        )
        long refreshTokenExpiresIn
) {

    public static AdminTokenRefreshResponse from(TokenRefreshResult result) {
        IssuedAccessToken accessToken = result.accessToken();
        IssuedRefreshToken refreshToken = result.refreshToken();

        return new AdminTokenRefreshResponse(
                accessToken.value(),
                accessToken.expiresIn().toSeconds(),
                refreshToken.value(),
                refreshToken.expiresIn().toSeconds());
    }
}
