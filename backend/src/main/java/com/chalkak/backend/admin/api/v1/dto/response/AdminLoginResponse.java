package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.AdminLoginResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record AdminLoginResponse(
        UUID adminId,
        String username,

        @Schema(description = "Authorization: Bearer 헤더에 사용할 관리자 액세스 토큰")
        String accessToken,

        @Schema(description = "발급 시점부터의 액세스 토큰 유효 시간(초)", example = "900")
        long expiresIn,

        @Schema(
                description = "관리자 액세스 토큰 재발급에 사용하는 리프레시 토큰."
                        + " 재발급하면 함께 회전하므로 응답의 새 값으로 교체해야 한다"
        )
        String refreshToken,

        @Schema(
                description = "발급 시점부터의 리프레시 토큰 유효 시간(초)",
                example = "2592000"
        )
        long refreshTokenExpiresIn
) {

    public static AdminLoginResponse from(AdminLoginResult result) {
        return new AdminLoginResponse(
                result.adminId(),
                result.username(),
                result.accessToken().value(),
                result.accessToken().expiresIn().toSeconds(),
                result.refreshToken().value(),
                result.refreshToken().expiresIn().toSeconds()
        );
    }
}
