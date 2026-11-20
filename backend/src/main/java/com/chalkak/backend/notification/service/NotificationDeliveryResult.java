package com.chalkak.backend.notification.service;

/** 외부 응답 원문이나 Webhook을 보관하지 않는 전송 결과. */
public record NotificationDeliveryResult(
        boolean sent,
        boolean retryable,
        String error,
        Integer httpStatus,
        Long retryAfterSeconds
) {
}
