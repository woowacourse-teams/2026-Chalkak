package com.chalkak.backend.jev.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** 물리 호출 한 번의 결과. 원응답은 DB에만 보관하고 로그에는 쓰지 않는다. */
public record JevCallResult(
        UUID callId,
        Instant startedAt,
        Instant receivedAt,
        String rawResponse,
        String errorCode,
        Integer httpStatus,
        Long latencyMs,
        Integer retryAfterSeconds
) {

    private static final Set<String> RETRYABLE = Set.of(
            "CONNECT_ERROR",
            "TIMEOUT",
            "NETWORK_ERROR",
            "RESPONSE_LOST"
    );

    @JsonIgnore
    public boolean isRetryable() {
        return RETRYABLE.contains(errorCode == null ? "" : errorCode)
                || (httpStatus != null && (httpStatus == 429 || httpStatus == 500
                        || httpStatus == 502 || httpStatus == 503 || httpStatus == 504));
    }
}
