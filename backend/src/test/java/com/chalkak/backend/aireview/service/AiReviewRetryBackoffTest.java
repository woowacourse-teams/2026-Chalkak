package com.chalkak.backend.aireview.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AiReviewRetryBackoffTest {

    @ParameterizedTest
    @CsvSource({
            "1, 10000",
            "2, 30000",
            "3, 30000"
    })
    @DisplayName("지터가 중간값이면 첫 재시도는 10초, 그 뒤는 30초를 기다린다")
    void delayAfter_middleJitter_returnsBaseDelay(int roundAttempt, long expectedMillis) {
        // Given
        AiReviewRetryBackoff backoff = new AiReviewRetryBackoff(() -> 0.5);

        // When
        Duration delay = backoff.delayAfter(roundAttempt);

        // Then
        assertThat(delay).isEqualTo(Duration.ofMillis(expectedMillis));
    }

    @ParameterizedTest
    @CsvSource({
            "0.0, 1, 8000",
            "1.0, 1, 12000",
            "0.0, 2, 24000",
            "1.0, 2, 36000"
    })
    @DisplayName("지터는 기본 대기 시간의 ±20% 범위 경계를 넘지 않는다")
    void delayAfter_jitterBounds_staysWithinTwentyPercent(
            double jitter,
            int roundAttempt,
            long expectedMillis
    ) {
        // Given
        AiReviewRetryBackoff backoff = new AiReviewRetryBackoff(() -> jitter);

        // When
        Duration delay = backoff.delayAfter(roundAttempt);

        // Then
        assertThat(delay).isEqualTo(Duration.ofMillis(expectedMillis));
    }
}
