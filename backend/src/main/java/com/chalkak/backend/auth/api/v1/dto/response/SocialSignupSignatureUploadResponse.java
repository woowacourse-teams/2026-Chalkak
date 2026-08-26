package com.chalkak.backend.auth.api.v1.dto.response;

import com.chalkak.backend.auth.service.SocialSignupSignatureUploadResult;
import java.util.UUID;

public record SocialSignupSignatureUploadResponse(
        UUID uploadId,
        String uploadUrl,
        long expiresInSeconds,
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
