package com.chalkak.backend.auth.domain;

import java.time.Duration;

public record IssuedRefreshToken(
        String value,
        Duration expiresIn
) {
}
