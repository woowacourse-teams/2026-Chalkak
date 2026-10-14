package com.chalkak.backend.notification.domain;

import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PushDeadlinePolicy {

    private static final Duration LIFETIME = Duration.ofMinutes(30);

    public static Instant calculateExpiresAt(Instant occurredAt) {
        return occurredAt.plus(LIFETIME);
    }

    public static boolean isExpiredAt(Instant expiresAt, Instant now) {
        return !now.isBefore(expiresAt);
    }

    public static boolean isValidLifetime(Instant occurredAt, Instant expiresAt) {
        return Duration.between(occurredAt, expiresAt).equals(LIFETIME);
    }
}
