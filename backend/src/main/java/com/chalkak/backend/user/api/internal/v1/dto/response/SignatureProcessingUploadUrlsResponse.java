package com.chalkak.backend.user.api.internal.v1.dto.response;

import com.chalkak.backend.user.repository.SignatureProcessingImageUpload;

public record SignatureProcessingUploadUrlsResponse(
        String originalUploadUrl,
        String thumbnailUploadUrl,
        String contentType,
        String cacheControl
) {

    public static SignatureProcessingUploadUrlsResponse from(
            SignatureProcessingImageUpload upload
    ) {
        return new SignatureProcessingUploadUrlsResponse(
                upload.originalUploadUrl(),
                upload.thumbnailUploadUrl(),
                upload.contentType(),
                upload.cacheControl()
        );
    }
}
