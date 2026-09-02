package com.chalkak.backend.notification.domain;

public enum NotificationOutboxStatus {
    PENDING,
    PROCESSING,
    RETRY,
    SENT,
    FAILED
}
