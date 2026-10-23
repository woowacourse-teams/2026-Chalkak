package com.chalkak.backend.notification.service;

import java.util.Map;
import org.slf4j.spi.LoggingEventBuilder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class PushWorkerTimingLog {

    private PushWorkerTimingLog() {
    }

    public static void logSummary(
            PushMessage message,
            int targetCount,
            Map<DevicePushResult.Status, Integer> counts,
            boolean retryable,
            PushWorkerDbTiming dbTiming
    ) {
        logSafely(() -> {
            var event = createSummaryEvent(message, dbTiming);
            event.addKeyValue("processingCompleted", true)
                    .addKeyValue("targetCount", targetCount)
                    .addKeyValue("acceptedCount",
                            counts.getOrDefault(DevicePushResult.Status.ACCEPTED, 0))
                    .addKeyValue("retryableCount",
                            counts.getOrDefault(DevicePushResult.Status.RETRYABLE, 0))
                    .addKeyValue("permanentFailureCount",
                            counts.getOrDefault(DevicePushResult.Status.PERMANENT_FAILURE, 0))
                    .addKeyValue("invalidTokenCount",
                            counts.getOrDefault(DevicePushResult.Status.INVALID_TOKEN, 0))
                    .addKeyValue("skippedCount",
                            counts.getOrDefault(DevicePushResult.Status.SKIPPED, 0))
                    .addKeyValue("retryable", retryable)
                    .log("푸시 기기 처리 결과 집계");
        });
    }

    public static void logSummaryFailed(
            PushMessage message,
            PushWorkerDbTiming dbTiming,
            RuntimeException exception
    ) {
        logSafely(() -> createSummaryEvent(message, dbTiming)
                .addKeyValue("processingCompleted", false)
                .addKeyValue("errorCode", exception.getClass().getSimpleName())
                .log("푸시 처리 중단 시점의 DB 계측 집계"));
    }

    private static LoggingEventBuilder createSummaryEvent(
            PushMessage message,
            PushWorkerDbTiming dbTiming
    ) {
        // 실패 원인은 기존 오류 로그로 추적한다. 요약은 ERROR 지표를 중복 증가시키지 않는다.
        return log.atInfo().addKeyValue("type", "notification")
                .addKeyValue("stage", "worker_summary")
                .addKeyValue("eventId", message.eventId())
                .addKeyValue("notificationId", message.notificationId())
                .addKeyValue("userId", message.userId())
                .addKeyValue("notificationType", message.type())
                .addKeyValue("dbTransactionCount", dbTiming.getTransactions().size())
                .addKeyValue("dbTransactionDurationMs", dbTiming.getDurationMs())
                .addKeyValue("dbTransactions", dbTiming.getTransactions());
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
