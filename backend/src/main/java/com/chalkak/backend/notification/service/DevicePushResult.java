package com.chalkak.backend.notification.service;

import java.time.Duration;

public record DevicePushResult(Status status, String errorCode, Duration retryAfter) {
    public static DevicePushResult skipped(String reason) {
        return new DevicePushResult(Status.SKIPPED, reason, Duration.ZERO);
    }

    public boolean isAccepted() {
        return status == Status.ACCEPTED;
    }

    public boolean isRetryable() {
        return status == Status.RETRYABLE;
    }

    public boolean isInvalidToken() {
        return status == Status.INVALID_TOKEN;
    }

    public boolean isPermanentFailure() {
        return status == Status.PERMANENT_FAILURE;
    }

    public enum Status {
        ACCEPTED, SKIPPED, RETRYABLE, INVALID_TOKEN, PERMANENT_FAILURE
    }
}
