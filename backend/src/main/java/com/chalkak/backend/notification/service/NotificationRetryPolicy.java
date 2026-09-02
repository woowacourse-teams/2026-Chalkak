package com.chalkak.backend.notification.service;

import java.time.Duration;

public class NotificationRetryPolicy {

    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final Duration DEFAULT_INITIAL_DELAY = Duration.ofSeconds(30);
    private static final Duration DEFAULT_MAX_DELAY = Duration.ofMinutes(10);
    private static final Duration MAX_PROVIDER_RETRY_AFTER = Duration.ofDays(1);

    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maxDelay;

    public NotificationRetryPolicy() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_DELAY, DEFAULT_MAX_DELAY);
    }

    public NotificationRetryPolicy(
            int maxAttempts,
            Duration initialDelay,
            Duration maxDelay
    ) {
        validateConfiguration(maxAttempts, initialDelay, maxDelay);
        this.maxAttempts = maxAttempts;
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
    }

    public boolean canRetry(int attemptCount) {
        validateAttemptCount(attemptCount);
        return attemptCount < maxAttempts;
    }

    public boolean canAttempt(int attemptCount) {
        validateAttemptCount(attemptCount);
        return attemptCount <= maxAttempts;
    }

    public Duration calculateDelay(int attemptCount, Duration retryAfter) {
        validateAttemptCount(attemptCount);
        Duration exponentialDelay = calculateExponentialDelay(attemptCount);
        if (retryAfter == null || retryAfter.isZero() || retryAfter.isNegative()) {
            return exponentialDelay;
        }
        Duration boundedRetryAfter = retryAfter.compareTo(MAX_PROVIDER_RETRY_AFTER) > 0
                ? MAX_PROVIDER_RETRY_AFTER
                : retryAfter;
        if (boundedRetryAfter.compareTo(exponentialDelay) > 0) {
            return boundedRetryAfter;
        }
        return exponentialDelay;
    }

    private Duration calculateExponentialDelay(int attemptCount) {
        Duration delay = initialDelay;
        for (int attempt = 1; attempt < attemptCount; attempt++) {
            if (delay.compareTo(maxDelay) >= 0) {
                return maxDelay;
            }
            delay = doubleCapped(delay);
        }
        return delay;
    }

    private Duration doubleCapped(Duration delay) {
        Duration doubled;
        try {
            doubled = delay.multipliedBy(2);
        } catch (ArithmeticException exception) {
            return maxDelay;
        }
        if (doubled.compareTo(maxDelay) > 0) {
            return maxDelay;
        }
        return doubled;
    }

    private void validateConfiguration(
            int configuredMaxAttempts,
            Duration configuredInitialDelay,
            Duration configuredMaxDelay
    ) {
        if (configuredMaxAttempts <= 0) {
            throw new IllegalArgumentException("알림 최대 시도 횟수는 1 이상이어야 합니다.");
        }
        validatePositiveDelay(configuredInitialDelay, "초기 재시도 지연 시간");
        validatePositiveDelay(configuredMaxDelay, "최대 재시도 지연 시간");
        if (configuredInitialDelay.compareTo(configuredMaxDelay) > 0) {
            throw new IllegalArgumentException(
                    "알림 초기 재시도 지연 시간은 최대 지연 시간보다 길 수 없습니다."
            );
        }
    }

    private void validatePositiveDelay(Duration delay, String name) {
        if (delay == null || delay.isZero() || delay.isNegative()) {
            throw new IllegalArgumentException(name + "은 0보다 길어야 합니다.");
        }
    }

    private void validateAttemptCount(int attemptCount) {
        if (attemptCount <= 0) {
            throw new IllegalArgumentException("알림 시도 횟수는 1 이상이어야 합니다.");
        }
    }
}
