package com.chalkak.backend.post.repository;

public record PostProcessingImageUpload(
        String originalUploadUrl,
        String thumbnailUploadUrl,
        String contentType,
        String cacheControl
) {
}
