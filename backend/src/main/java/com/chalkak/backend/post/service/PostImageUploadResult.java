package com.chalkak.backend.post.service;

import java.util.UUID;

public record PostImageUploadResult(
        UUID uploadId,
        String uploadUrl,
        long expiresInSeconds,
        String contentType,
        long maxBytes
) {
}
