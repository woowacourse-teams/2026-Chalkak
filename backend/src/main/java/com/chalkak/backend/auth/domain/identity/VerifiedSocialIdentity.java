package com.chalkak.backend.auth.domain.identity;

public record VerifiedSocialIdentity(
        SocialProvider provider,
        String subject,
        String email
) {
}
