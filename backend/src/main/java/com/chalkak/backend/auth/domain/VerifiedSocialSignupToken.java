package com.chalkak.backend.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record VerifiedSocialSignupToken(
        SocialProvider provider,
        String subject,
        UUID uploadId,
        String email,
        String tokenId,
        Instant expiresAt
) {
}
