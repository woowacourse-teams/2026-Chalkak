package com.chalkak.backend.aireview.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.aireview.service.AiReviewGeneration.Attempt;
import com.chalkak.backend.aireview.service.AiReviewGenerationException.FailureType;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiReviewGenerationExceptionTest {

    @Test
    @DisplayName("of는 실패 유형만 담고 응답 기록과 대기 시간 힌트는 비워 둔다")
    void of_onlyFailureType_hasNoAttemptAndNoRetryAfter() {
        // When
        AiReviewGenerationException exception = AiReviewGenerationException
                .of(FailureType.INVALID_OUTPUT);

        // Then
        assertThat(exception.getFailureType()).isEqualTo(FailureType.INVALID_OUTPUT);
        assertThat(exception.findAttempt()).isEmpty();
        assertThat(exception.findRetryAfter()).isEmpty();
        assertThat(exception.getCause()).isNull();
        assertThat(exception.getMessage()).contains("INVALID_OUTPUT");
    }

    @Test
    @DisplayName("withAttempt는 받은 응답 기록을 담는다")
    void withAttempt_keepsAttempt() {
        // Given
        Attempt attempt = new Attempt(
                null,
                Duration.ofSeconds(1),
                "resp_1"
        );

        // When
        AiReviewGenerationException exception = AiReviewGenerationException
                .withAttempt(FailureType.INCOMPLETE, attempt);

        // Then
        assertThat(exception.getFailureType()).isEqualTo(FailureType.INCOMPLETE);
        assertThat(exception.findAttempt()).contains(attempt);
        assertThat(exception.findRetryAfter()).isEmpty();
    }

    @Test
    @DisplayName("withRetryAfter는 외부 서비스가 알려 준 대기 시간을 담는다")
    void withRetryAfter_keepsRetryAfter() {
        // When
        AiReviewGenerationException exception = AiReviewGenerationException
                .withRetryAfter(FailureType.RATE_LIMITED, Duration.ofSeconds(60));

        // Then
        assertThat(exception.getFailureType()).isEqualTo(FailureType.RATE_LIMITED);
        assertThat(exception.findRetryAfter()).contains(Duration.ofSeconds(60));
        assertThat(exception.findAttempt()).isEmpty();
    }

    @Test
    @DisplayName("withCause는 원인 예외를 버리지 않고 보존한다")
    void withCause_preservesCause() {
        // Given
        IOException cause = new IOException("connection reset");

        // When
        AiReviewGenerationException exception = AiReviewGenerationException
                .withCause(FailureType.UPSTREAM_ERROR, cause);

        // Then
        assertThat(exception.getFailureType()).isEqualTo(FailureType.UPSTREAM_ERROR);
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getMessage()).doesNotContain("connection reset");
    }
}
