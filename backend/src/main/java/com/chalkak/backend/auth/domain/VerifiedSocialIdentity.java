package com.chalkak.backend.auth.domain;

public record VerifiedSocialIdentity(
        SocialProvider provider,
        String subject,
        String email
) {
}
