package com.chalkak.backend.common.logging;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 1분마다 런타임 상태를 한 줄로 남긴다.
 *
 * <p>
 * 이 작업 하나만 끄고 싶을 때를 위한 플래그를 빈에 건다. 스케줄링 기능 자체를 끄는 것은 {@code chalkak.scheduling.enabled}이다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "chalkak.monitoring.runtime-log", name = "enabled", havingValue = "true")
public class RuntimeSnapshotLogger {

    private static final Logger RUNTIME_LOG = LoggerFactory.getLogger("chalkak.runtime");

    private final RuntimeSnapshotReader reader;

    @Scheduled(fixedRate = 60_000)
    public void logSnapshot() {
        RuntimeSnapshot snapshot = reader.read();
        LoggingEventBuilder event = RUNTIME_LOG.atInfo()
            .addKeyValue(LogFields.TYPE, LogFields.TYPE_RUNTIME);
        for (Map.Entry<String, Object> field : snapshot.logFields().entrySet()) {
            event = event.addKeyValue(field.getKey(), field.getValue());
        }
        event.log("런타임 상태");
    }
}
