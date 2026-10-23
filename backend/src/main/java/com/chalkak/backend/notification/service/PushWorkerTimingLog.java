package com.chalkak.backend.notification.service;

import com.chalkak.backend.common.logging.Elapsed;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class PushWorkerTimingLog {

    private PushWorkerTimingLog() {
    }

    public static void logTransaction(
            PushMessage message,
            UUID deviceId,
            String operation,
            Elapsed elapsed,
            boolean completed
    ) {
        logSafely(() -> {
            // 실패 원인은 기존 오류 로그에서 기록한다. 계측은 ERROR 지표를 중복 증가시키지 않는다.
            log.atInfo().addKeyValue("type", "notification")
                    .addKeyValue("stage", "worker_db_transaction")
                    .addKeyValue("operation", operation)
                    .addKeyValue("eventId", message.eventId())
                    .addKeyValue("notificationId", message.notificationId())
                    .addKeyValue("userId", message.userId())
                    .addKeyValue("deviceId", deviceId)
                    .addKeyValue("transactionCompleted", completed)
                    .addKeyValue("dbTransactionDurationMs", elapsed.toMillis())
                    .log("푸시 DB 트랜잭션 처리 시간");
        });
    }

    public static void logFcmRequest(
            DevicePushRequest request,
            Elapsed elapsed,
            boolean accepted
    ) {
        logSafely(() -> {
            PushMessage message = request.message();
            log.atInfo().addKeyValue("type", "notification")
                    .addKeyValue("stage", "fcm_request_completed")
                    .addKeyValue("eventId", message.eventId())
                    .addKeyValue("notificationId", message.notificationId())
                    .addKeyValue("userId", message.userId())
                    .addKeyValue("sdkAccepted", accepted)
                    .addKeyValue("fcmRequestDurationMs", elapsed.toMillis())
                    .log("FCM SDK 요청 처리 시간");
        });
    }

    private static void logSafely(Runnable logging) {
        try {
            logging.run();
        } catch (RuntimeException exception) {
            // 같은 로깅 경로로 재출력하지 않는다. 원문·스택·토큰 없이 journald 조사용으로 남긴다.
            System.err.println("알림 계측 로그 기록 실패: " + exception.getClass().getSimpleName());
        }
    }
}
