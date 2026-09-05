package com.chalkak.backend.auth.api.v1.dto.response;

import com.chalkak.backend.auth.domain.IssuedAccessToken;
import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import com.chalkak.backend.auth.service.SocialLoginResult;
import com.chalkak.backend.auth.service.SocialLoginStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SocialLoginResponse(
        SocialLoginStatus status,

        @Schema(description = "LOGIN_SUCCESS일 때만 내려준다", nullable = true)
        UUID userId,

        @Schema(
                description = "LOGIN_SUCCESS일 때만 내려주는 액세스 토큰",
                nullable = true
        )
        String accessToken,

        @Schema(
                description = "발급 시점부터의 액세스 토큰 유효 시간(초)."
                        + " LOGIN_SUCCESS일 때만 내려준다",
                example = "900",
                nullable = true
        )
        Long expiresIn,

        @Schema(
                description = "액세스 토큰 재발급에 사용하는 리프레시 토큰."
                        + " LOGIN_SUCCESS일 때만 내려준다",
                nullable = true
        )
        String refreshToken,

        @Schema(
                description = "발급 시점부터의 리프레시 토큰 유효 시간(초)."
                        + " LOGIN_SUCCESS일 때만 내려준다",
                example = "2592000",
                nullable = true
        )
        Long refreshTokenExpiresIn
) {

    public static SocialLoginResponse from(SocialLoginResult result) {
        IssuedAccessToken accessToken = result.accessToken();
        if (accessToken == null) {
            return new SocialLoginResponse(
                    result.status(),
                    result.userId(),
                    null,
                    null,
                    null,
                    null);
        }
        IssuedRefreshToken refreshToken = result.refreshToken();
        return new SocialLoginResponse(
                result.status(),
                result.userId(),
                accessToken.value(),
                accessToken.expiresIn().toSeconds(),
                refreshToken.value(),
                refreshToken.expiresIn().toSeconds());
    }
}
