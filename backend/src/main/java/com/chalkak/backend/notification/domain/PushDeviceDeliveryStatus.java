package com.chalkak.backend.notification.domain;

public enum PushDeviceDeliveryStatus {
    SENDING, ACCEPTED, RETRYABLE, UNKNOWN, INVALID_TOKEN, PERMANENT_FAILURE, SKIPPED
}
