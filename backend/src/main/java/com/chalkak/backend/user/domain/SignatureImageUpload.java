package com.chalkak.backend.user.domain;

import java.util.UUID;

public record SignatureImageUpload(
        UUID uploadId,
        String uploadUrl,
        long expiresInSeconds
) {
}
