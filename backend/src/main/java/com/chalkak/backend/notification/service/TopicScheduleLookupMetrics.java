package com.chalkak.backend.notification.service;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class TopicScheduleLookupMetrics {

    private static final long WINDOW_SECONDS = 300;
    private static final long SLOW_LOOKUP_THRESHOLD_MS = 500;

    // 주제 푸시 스케줄러의 단일 스레드에서만 갱신한다.
    private Instant windowStartedAt;
    private long lookupCount;
    private long failureCount;
    private long totalDurationMs;
    private long maxDurationMs;

    void startWindow(Instant startedAt) {
        Instant window = Instant.ofEpochSecond(Math.floorDiv(startedAt.getEpochSecond(),
                WINDOW_SECONDS) * WINDOW_SECONDS);
        if (!window.equals(windowStartedAt)) {
            finishWindow(window);
        }
    }

    void record(long durationMs, RuntimeException failure) {
        lookupCount++;
        totalDurationMs += durationMs;
        maxDurationMs = Math.max(maxDurationMs, durationMs);
        if (failure != null) {
            failureCount++;
            log.atWarn().addKeyValue("type", "notification")
                    .addKeyValue("stage", "topic_schedule_lookup_failed")
                    .addKeyValue("durationMs", durationMs)
                    .addKeyValue("errorCode", failure.getClass().getSimpleName())
                    .log("주제 푸시 예약 목록 조회 실패");
            return;
        }
        if (durationMs >= SLOW_LOOKUP_THRESHOLD_MS) {
            log.atWarn().addKeyValue("type", "notification")
                    .addKeyValue("stage", "topic_schedule_lookup_slow")
                    .addKeyValue("durationMs", durationMs)
                    .addKeyValue("thresholdMs", SLOW_LOOKUP_THRESHOLD_MS)
                    .log("주제 푸시 예약 목록 조회 지연");
        }
    }

    private void finishWindow(Instant nextWindow) {
        if (lookupCount > 0) {
            log.atInfo().addKeyValue("type", "notification")
                    .addKeyValue("stage", "topic_schedule_lookup_summary")
                    .addKeyValue("windowStartedAt", windowStartedAt)
                    .addKeyValue("lookupCount", lookupCount)
                    .addKeyValue("failureCount", failureCount)
                    .addKeyValue("totalDurationMs", totalDurationMs)
                    .addKeyValue("avgDurationMs", (double) totalDurationMs / lookupCount)
                    .addKeyValue("maxDurationMs", maxDurationMs)
                    .log("주제 푸시 예약 목록 조회 5분 집계");
        }
        windowStartedAt = nextWindow;
        lookupCount = 0;
        failureCount = 0;
        totalDurationMs = 0;
        maxDurationMs = 0;
    }
}
