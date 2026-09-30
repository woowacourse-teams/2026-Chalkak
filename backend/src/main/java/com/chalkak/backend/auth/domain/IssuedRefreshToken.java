package com.chalkak.backend.auth.domain;

import java.time.Duration;
import java.util.UUID;

public record IssuedRefreshToken(
        String value,
        Duration expiresIn,
        UUID sessionId) {

    public IssuedRefreshToken(String value, Duration expiresIn) {
        this(value, expiresIn, null);
    }
}
