package com.chalkak.backend.auth.domain.token;

import java.time.Duration;

public record IssuedAccessToken(
        String value,
        Duration expiresIn) {
}
