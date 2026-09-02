package com.chalkak.backend.notification.service;

public enum NotificationSendOutcome {
    SENT,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE
}
