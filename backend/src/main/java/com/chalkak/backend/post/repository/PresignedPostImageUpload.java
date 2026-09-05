package com.chalkak.backend.post.repository;

public record PresignedPostImageUpload(
        String uploadUrl,
        long expiresInSeconds,
        String contentType,
        long maxBytes
) {
}
