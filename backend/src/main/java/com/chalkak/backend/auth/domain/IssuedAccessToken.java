package com.chalkak.backend.auth.domain;

import java.time.Duration;

public record IssuedAccessToken(
        String value,
        Duration expiresIn
) {
}
