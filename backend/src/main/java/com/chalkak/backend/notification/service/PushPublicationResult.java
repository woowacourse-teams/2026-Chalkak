package com.chalkak.backend.notification.service;

public record PushPublicationResult(String messageId, String errorCode, boolean retryable) {

    public static PushPublicationResult accepted(String messageId) {
        return new PushPublicationResult(messageId, null, false);
    }

    public static PushPublicationResult retry(String errorCode) {
        return new PushPublicationResult(null, errorCode, true);
    }

    public static PushPublicationResult failed(String errorCode) {
        return new PushPublicationResult(null, errorCode, false);
    }

    public boolean isAccepted() {
        return messageId != null && !messageId.isBlank();
    }
}
