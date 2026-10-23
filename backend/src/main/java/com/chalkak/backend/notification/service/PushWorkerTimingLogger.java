package com.chalkak.backend.notification.service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PushWorkerTimingLogger {

    public void logTransaction(
            PushMessage message,
            UUID deviceId,
            String operation,
            long startedAt,
            boolean completed
    ) {
        // 실패 원인은 기존 오류 로그에서 기록한다. 계측은 ERROR 지표를 중복 증가시키지 않는다.
        log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_db_transaction")
                .addKeyValue("operation", operation)
                .addKeyValue("eventId", message.eventId())
                .addKeyValue("notificationId", message.notificationId())
                .addKeyValue("userId", message.userId())
                .addKeyValue("deviceId", deviceId)
                .addKeyValue("transactionCompleted", completed)
                .addKeyValue("dbTransactionDurationMs", elapsedMillis(startedAt))
                .log("푸시 DB 트랜잭션 처리 시간");
    }

    public void logFcmRequest(
            DevicePushRequest request,
            long startedAt,
            boolean accepted
    ) {
        PushMessage message = request.message();
        log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "fcm_request_completed")
                .addKeyValue("eventId", message.eventId())
                .addKeyValue("notificationId", message.notificationId())
                .addKeyValue("userId", message.userId())
                .addKeyValue("sdkAccepted", accepted)
                .addKeyValue("fcmRequestDurationMs", elapsedMillis(startedAt))
                .log("FCM SDK 요청 처리 시간");
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
