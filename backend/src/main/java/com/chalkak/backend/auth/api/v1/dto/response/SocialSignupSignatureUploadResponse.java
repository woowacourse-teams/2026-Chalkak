package com.chalkak.backend.auth.api.v1.dto.response;

import com.chalkak.backend.auth.service.SocialSignupSignatureUploadResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record SocialSignupSignatureUploadResponse(
        UUID uploadId,

        @Schema(
                description = "서명 PNG 이미지를 업로드할 S3 Presigned PUT URL. "
                        + "Content-Type은 image/png이며 최대 크기는 1 MiB"
        )
        String uploadUrl,

        @Schema(
                description = "S3 Presigned URL 만료까지 남은 시간(초). "
                        + "signupToken의 만료 시간이 아님",
                example = "300"
        )
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
