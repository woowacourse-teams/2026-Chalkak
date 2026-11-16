package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class TopicScheduleLookupMetricsTest {

    private static final Instant WINDOW_START = Instant.parse("2026-11-16T10:00:00Z");

    private final TopicScheduleLookupMetrics metrics = new TopicScheduleLookupMetrics();
    private final Logger logger = (Logger) LoggerFactory
            .getLogger(TopicScheduleLookupMetrics.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("정상 조회는 구간 경계에서 한 줄로 요약하고 다음 구간에는 섞지 않는다")
    void startWindow_completedWindow_logsSummaryOnce() {
        // Given
        for (int minute = 0; minute < 5; minute++) {
            metrics.startWindow(WINDOW_START.plusSeconds(60L * minute));
            metrics.record(10L * (minute + 1), null);
        }
        assertThat(appender.list).isEmpty();

        // When & Then
        metrics.startWindow(WINDOW_START.plusSeconds(300));
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(fields(event))
                    .containsEntry("stage", "topic_schedule_lookup_summary")
                    .containsEntry("windowStartedAt", WINDOW_START)
                    .containsEntry("lookupCount", 5L)
                    .containsEntry("failureCount", 0L)
                    .containsEntry("avgDurationMs", 30.0)
                    .containsEntry("maxDurationMs", 50L)
                    .containsEntry("totalDurationMs", 150L);
        });

        metrics.record(20, null);
        metrics.startWindow(WINDOW_START.plusSeconds(600));
        assertThat(appender.list).hasSize(2);
        assertThat(fields(appender.list.getLast()))
                .containsEntry("lookupCount", 1L)
                .containsEntry("avgDurationMs", 20.0)
                .containsEntry("maxDurationMs", 20L);
    }

    @Test
    @DisplayName("실패는 즉시 기록하고 다음 집계에 시간과 실패 건수를 포함한다")
    void record_failedLookup_logsDetailsAndIncludesInSummary() {
        // Given
        metrics.startWindow(WINDOW_START);

        // When
        metrics.record(800, new IllegalStateException());

        // Then
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(fields(event))
                    .containsEntry("stage", "topic_schedule_lookup_failed")
                    .containsEntry("errorCode", "IllegalStateException")
                    .containsEntry("durationMs", 800L);
        });
        metrics.startWindow(WINDOW_START.plusSeconds(300));
        assertThat(fields(appender.list.getLast()))
                .containsEntry("stage", "topic_schedule_lookup_summary")
                .containsEntry("failureCount", 1L)
                .containsEntry("avgDurationMs", 800.0);
    }

    @Test
    @DisplayName("성공한 조회는 500ms부터 지연 상세를 즉시 기록한다")
    void record_slowLookup_logsAtThreshold() {
        // Given
        metrics.startWindow(WINDOW_START);
        metrics.record(499, null);
        assertThat(appender.list).isEmpty();

        // When
        metrics.record(500, null);

        // Then
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(fields(event))
                    .containsEntry("stage", "topic_schedule_lookup_slow")
                    .containsEntry("durationMs", 500L)
                    .containsEntry("thresholdMs", 500L);
        });
    }

    private Map<String, Object> fields(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(field -> field.key, field -> field.value));
    }
}
