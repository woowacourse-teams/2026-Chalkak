package com.chalkak.backend.post.api.internal.v1.dto.response;

import com.chalkak.backend.post.repository.PostProcessingImageUpload;

public record PostProcessingUploadUrlsResponse(
        String originalUploadUrl,
        String thumbnailUploadUrl,
        String contentType,
        String cacheControl
) {

    public static PostProcessingUploadUrlsResponse from(PostProcessingImageUpload upload) {
        return new PostProcessingUploadUrlsResponse(
                upload.originalUploadUrl(),
                upload.thumbnailUploadUrl(),
                upload.contentType(),
                upload.cacheControl()
        );
    }
}
