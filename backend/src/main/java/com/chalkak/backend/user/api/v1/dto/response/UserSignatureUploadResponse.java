package com.chalkak.backend.user.api.v1.dto.response;

import com.chalkak.backend.user.repository.SignatureImageUpload;
import java.util.UUID;

public record UserSignatureUploadResponse(
        UUID uploadId,
        String uploadUrl,
        long expiresInSeconds
) {

    public static UserSignatureUploadResponse from(SignatureImageUpload upload) {
        return new UserSignatureUploadResponse(
                upload.uploadId(),
                upload.uploadUrl(),
                upload.expiresInSeconds()
        );
    }
}
