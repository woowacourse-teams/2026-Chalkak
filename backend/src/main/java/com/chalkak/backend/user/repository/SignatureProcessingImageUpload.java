package com.chalkak.backend.user.repository;

public record SignatureProcessingImageUpload(
        String originalUploadUrl,
        String thumbnailUploadUrl,
        String contentType,
        String cacheControl
) {
}
