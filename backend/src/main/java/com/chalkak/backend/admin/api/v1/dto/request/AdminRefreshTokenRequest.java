package com.chalkak.backend.admin.api.v1.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AdminRefreshTokenRequest(
        @Schema(description = "관리자 로그인 또는 직전 재발급 응답에서 받은 리프레시 토큰")
        @NotBlank(message = "리프레시 토큰은 필수입니다.")
        String refreshToken
) {
}
