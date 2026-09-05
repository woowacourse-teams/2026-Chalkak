package com.chalkak.backend.auth.domain;

import java.time.Instant;

public record IssuedSocialSignupToken(
        String value,
        Instant expiresAt
) {

    public IssuedSocialSignupToken(String value) {
        this(value, null);
    }
}
