package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.AdminLoginResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record AdminLoginResponse(
        UUID adminId,
        String username,

        @Schema(description = "Authorization: Bearer 헤더에 사용할 관리자 액세스 토큰")
        String accessToken,

        @Schema(description = "발급 시점부터의 액세스 토큰 유효 시간(초)", example = "3600")
        long expiresIn
) {

    public static AdminLoginResponse from(AdminLoginResult result) {
        return new AdminLoginResponse(
                result.adminId(),
                result.username(),
                result.accessToken().value(),
                result.accessToken().expiresIn().toSeconds()
        );
    }
}
