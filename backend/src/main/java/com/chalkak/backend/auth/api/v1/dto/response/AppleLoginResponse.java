package com.chalkak.backend.auth.api.v1.dto.response;

import com.chalkak.backend.auth.domain.IssuedAccessToken;
import com.chalkak.backend.auth.service.AppleLoginResult;
import com.chalkak.backend.auth.service.SocialLoginStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppleLoginResponse(
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
                example = "3600",
                nullable = true
        )
        Long expiresIn,

        @Schema(
                description = "SIGN_UP_REQUIRED일 때만 내려주는 5분 유효 회원가입 토큰",
                nullable = true
        )
        String signupToken
) {

    public static AppleLoginResponse from(AppleLoginResult result) {
        IssuedAccessToken accessToken = result.accessToken();
        if (accessToken == null) {
            return new AppleLoginResponse(
                    result.status(),
                    null,
                    null,
                    null,
                    result.signupToken().value());
        }
        return new AppleLoginResponse(
                result.status(),
                result.userId(),
                accessToken.value(),
                accessToken.expiresIn().toSeconds(),
                null);
    }
}
