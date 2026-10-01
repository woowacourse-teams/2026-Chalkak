package com.chalkak.backend.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class CurrentRequestIdTest {

    private final CurrentRequestId currentRequestId = new CurrentRequestId();

    @AfterEach
    void clearMdc() {
        MDC.remove(LogFields.REQUEST_ID);
    }

    @Test
    @DisplayName("MDC에 UUID 형식 requestId가 있으면 그 값을 그대로 돌려준다")
    void resolve_validRequestIdInMdc_returnsSameUuid() {
        // Given
        UUID requestId = UUID.randomUUID();
        MDC.put(LogFields.REQUEST_ID, requestId.toString());

        // When
        UUID resolved = currentRequestId.resolve();

        // Then
        assertThat(resolved).isEqualTo(requestId);
    }

    @Test
    @DisplayName("MDC에 requestId가 없으면 요청마다 새 UUID를 만든다")
    void resolve_noRequestIdInMdc_returnsNewUuid() {
        // When
        UUID first = currentRequestId.resolve();
        UUID second = currentRequestId.resolve();

        // Then
        assertThat(first).isNotNull();
        assertThat(second).isNotNull().isNotEqualTo(first);
    }

    @Test
    @DisplayName("MDC의 requestId가 UUID 형식이 아니면 그 값을 쓰지 않고 새 UUID를 만든다")
    void resolve_malformedRequestIdInMdc_returnsNewUuid() {
        // Given
        MDC.put(LogFields.REQUEST_ID, "not-a-uuid");

        // When
        UUID resolved = currentRequestId.resolve();

        // Then
        assertThat(resolved).isNotNull();
        assertThat(resolved.toString()).isNotEqualTo("not-a-uuid");
    }
}
