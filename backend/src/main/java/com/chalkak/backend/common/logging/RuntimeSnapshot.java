package com.chalkak.backend.common.logging;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 한 시점의 JVM·커넥션 풀 상태.
 *
 * <p>
 * 측정하지 못한 값은 0이 아니라 {@code null}이다. 0으로 채우면 "풀이 비었다"와 "풀이 없다"를 구별할 수 없어
 * 그래프가 거짓 값을 그리므로, 로그에서도 해당 키를 남기지 않는다.
 */
public record RuntimeSnapshot(
        Long heapUsedBytes,
        Long heapMaxBytes,
        Long gcTimeMsTotal,
        Integer threads,
        Integer hikariActive,
        Integer hikariPending
) {

    /** 측정된 값만 로그 키 이름으로 담아 선언 순서대로 돌려준다. */
    public Map<String, Object> logFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        putIfPresent(fields, LogFields.HEAP_USED_BYTES, heapUsedBytes);
        putIfPresent(fields, LogFields.HEAP_MAX_BYTES, heapMaxBytes);
        putIfPresent(fields, LogFields.GC_TIME_MS_TOTAL, gcTimeMsTotal);
        putIfPresent(fields, LogFields.THREADS, threads);
        putIfPresent(fields, LogFields.HIKARI_ACTIVE, hikariActive);
        putIfPresent(fields, LogFields.HIKARI_PENDING, hikariPending);
        return fields;
    }

    private void putIfPresent(Map<String, Object> fields, String key, Number value) {
        if (value == null) {
            return;
        }
        fields.put(key, value);
    }
}
