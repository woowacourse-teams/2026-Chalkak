package com.chalkak.backend.notification.service;

public record PushPublicationResult(
        Status status,
        String messageId,
        String errorCode,
        boolean retryable
) {

    public static PushPublicationResult accepted(String messageId) {
        return new PushPublicationResult(
                Status.ACCEPTED,
                messageId,
                null,
                false
        );
    }

    public static PushPublicationResult retry(String errorCode) {
        return new PushPublicationResult(
                Status.FAILED,
                null,
                errorCode,
                true
        );
    }

    public static PushPublicationResult unknown(String errorCode) {
        return new PushPublicationResult(
                Status.UNKNOWN,
                null,
                errorCode,
                true
        );
    }

    public static PushPublicationResult failed(String errorCode) {
        return new PushPublicationResult(
                Status.FAILED,
                null,
                errorCode,
                false
        );
    }

    public boolean isAccepted() {
        return status == Status.ACCEPTED && messageId != null && !messageId.isBlank();
    }

    public boolean isAcceptanceUnknown() {
        return status == Status.UNKNOWN;
    }

    public enum Status {
        ACCEPTED, FAILED, UNKNOWN
    }
}
