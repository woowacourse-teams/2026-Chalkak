package com.chalkak.backend.user.repository;

import java.util.UUID;

public record SignatureImageUpload(
        UUID uploadId,
        String uploadUrl,
        long expiresInSeconds
) {
}
