package com.chalkak.backend.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class RuntimeSnapshotLoggerTest {

    private final RuntimeSnapshotReader reader = mock(RuntimeSnapshotReader.class);
    private final RuntimeSnapshotLogger snapshotLogger = new RuntimeSnapshotLogger(reader);
    private final Logger runtimeLogger = (Logger) LoggerFactory.getLogger("chalkak.runtime");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        runtimeLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        runtimeLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("스냅샷의 값을 type=runtime key-value로 담은 INFO 로그 1건을 남긴다")
    void logSnapshot_allValuesPresent_logsOneRuntimeEvent() {
        // Given
        given(reader.read()).willReturn(new RuntimeSnapshot(400L, 1_000L, 42L, 40, 3, 1));

        // When
        snapshotLogger.logSnapshot();

        // Then
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(keyValue(event, "type")).isEqualTo("runtime");
        assertThat(keyValue(event, "heapUsedBytes")).isEqualTo(400L);
        assertThat(keyValue(event, "heapMaxBytes")).isEqualTo(1_000L);
        assertThat(keyValue(event, "gcTimeMsTotal")).isEqualTo(42L);
        assertThat(keyValue(event, "threads")).isEqualTo(40);
        assertThat(keyValue(event, "hikariActive")).isEqualTo(3);
        assertThat(keyValue(event, "hikariPending")).isEqualTo(1);
    }

    @Test
    @DisplayName("측정하지 못한 값은 0으로 채우지 않고 key-value에서 뺀다")
    void logSnapshot_absentValues_omitsKeys() {
        // Given
        given(reader.read()).willReturn(new RuntimeSnapshot(400L, null, null, 40, null, null));

        // When
        snapshotLogger.logSnapshot();

        // Then
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getKeyValuePairs())
                .extracting(pair -> pair.key)
                .containsExactly("type", "heapUsedBytes", "threads");
    }

    private Object keyValue(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream()
                .filter(pair -> pair.key.equals(key))
                .findFirst()
                .orElseThrow().value;
    }
}
