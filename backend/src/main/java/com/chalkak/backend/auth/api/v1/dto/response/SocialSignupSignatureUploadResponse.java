package com.chalkak.backend.auth.api.v1.dto.response;

import com.chalkak.backend.user.repository.SignatureImageUpload;
import java.util.UUID;

public record SocialSignupSignatureUploadResponse(
        UUID uploadId,
        String uploadUrl,
        long expiresInSeconds
) {

    public static SocialSignupSignatureUploadResponse from(
            SignatureImageUpload upload
    ) {
        return new SocialSignupSignatureUploadResponse(
                upload.uploadId(),
                upload.uploadUrl(),
                upload.expiresInSeconds());
    }
}
