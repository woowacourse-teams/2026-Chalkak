package com.chalkak.backend.notification.service;

import java.time.Instant;

public record DevicePushRequest(
        PushMessage message,
        String token,
        String title,
        String body,
        Instant expiresAt
) {
}
