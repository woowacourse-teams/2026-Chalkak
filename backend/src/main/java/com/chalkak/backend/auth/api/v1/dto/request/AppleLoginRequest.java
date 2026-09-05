package com.chalkak.backend.auth.api.v1.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AppleLoginRequest(
        @Schema(description = "iOS가 Apple 로그인 후 받은 ID Token")
        @NotBlank(message = "Apple ID Token은 필수입니다.")
        String idToken,

        @Schema(description = "iOS가 Apple 로그인 후 받은 일회용 Authorization Code")
        @NotBlank(message = "Apple Authorization Code는 필수입니다.")
        String authorizationCode,

        @Schema(description = "iOS가 Apple 인증 요청 전에 생성한 원본 nonce")
        @NotBlank(message = "Apple rawNonce는 필수입니다.")
        String rawNonce
) {
}
