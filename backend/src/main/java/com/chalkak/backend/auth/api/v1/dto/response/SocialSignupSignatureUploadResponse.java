package com.chalkak.backend.auth.api.v1.dto.response;

import com.chalkak.backend.auth.service.SocialSignupSignatureUploadResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record SocialSignupSignatureUploadResponse(
        UUID uploadId,
        String uploadUrl,
        long expiresInSeconds,
        @Schema(description = "검증된 소셜 계정과 업로드 식별자를 연결한 5분 유효 회원가입 토큰")
        String signupToken
) {

    public static SocialSignupSignatureUploadResponse from(
            SocialSignupSignatureUploadResult result
    ) {
        return new SocialSignupSignatureUploadResponse(
                result.upload().uploadId(),
                result.upload().uploadUrl(),
                result.upload().expiresInSeconds(),
                result.signupToken().value());
    }
}
