package com.chalkak.backend.auth.api.v1.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AppleSignupSignatureUploadRequest(
        @Schema(description = "Apple 로그인 응답에서 받은 5분 유효 회원가입 토큰")
        @NotBlank(message = "회원가입 토큰은 필수입니다.")
        String signupToken
) {
}
