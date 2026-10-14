package com.chalkak.backend.notification.service;

import java.time.Duration;

public record PushProcessingResult(boolean retryable, Duration retryAfter) {
    public static PushProcessingResult completed() {
        return new PushProcessingResult(false, Duration.ZERO);
    }
}
