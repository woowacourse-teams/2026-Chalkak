package com.chalkak.backend.auth.domain;

import java.util.UUID;

public record VerifiedSocialSignupToken(
        SocialProvider provider,
        String subject,
        UUID uploadId,
        String email
) {
}
