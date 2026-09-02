package com.chalkak.backend.notification.service;

import java.time.Duration;

public record NotificationSendResult(
        NotificationSendOutcome outcome,
        Duration retryAfter,
        String failureCode
) {

    public static NotificationSendResult sent() {
        return new NotificationSendResult(NotificationSendOutcome.SENT, null, null);
    }

    public static NotificationSendResult retryable(Duration retryAfter, String failureCode) {
        return new NotificationSendResult(
                NotificationSendOutcome.RETRYABLE_FAILURE,
                retryAfter,
                failureCode
        );
    }

    public static NotificationSendResult permanentFailure(String failureCode) {
        return new NotificationSendResult(
                NotificationSendOutcome.PERMANENT_FAILURE,
                null,
                failureCode
        );
    }
}
