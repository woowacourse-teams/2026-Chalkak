package com.chalkak.backend.aireview.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.aireview.service.AiReviewRetryBackoff.RetryDelay;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AiReviewRetryBackoffTest {

    @ParameterizedTest
    @CsvSource({
            "1, 10000",
            "2, 30000",
            "3, 30000"
    })
    @DisplayName("지터가 중간값이고 Retry-After가 없으면 첫 재시도는 10초, 그 뒤는 30초를 기다린다")
    void delayAfter_middleJitter_returnsBaseDelay(int roundAttempt, long expectedMillis) {
        // Given
        AiReviewRetryBackoff backoff = new AiReviewRetryBackoff(() -> 0.5);

        // When
        RetryDelay delay = backoff.delayAfter(
                roundAttempt,
                Optional.empty()
        );

        // Then
        assertThat(delay.delay()).isEqualTo(Duration.ofMillis(expectedMillis));
        assertThat(delay.retryAfterUsed()).isFalse();
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
        RetryDelay delay = backoff.delayAfter(
                roundAttempt,
                Optional.empty()
        );

        // Then
        assertThat(delay.delay()).isEqualTo(Duration.ofMillis(expectedMillis));
    }

    @Test
    @DisplayName("Retry-After가 백오프보다 짧으면 백오프를 쓴다")
    void delayAfter_shorterRetryAfter_usesBackoff() {
        // Given
        AiReviewRetryBackoff backoff = new AiReviewRetryBackoff(() -> 0.5);

        // When
        RetryDelay delay = backoff.delayAfter(
                2,
                Optional.of(Duration.ofSeconds(5))
        );

        // Then
        assertThat(delay.delay()).isEqualTo(Duration.ofSeconds(30));
        assertThat(delay.retryAfterUsed()).isFalse();
    }

    @Test
    @DisplayName("Retry-After가 백오프보다 길면 Retry-After를 쓴다")
    void delayAfter_longerRetryAfter_usesRetryAfter() {
        // Given
        AiReviewRetryBackoff backoff = new AiReviewRetryBackoff(() -> 0.5);

        // When
        RetryDelay delay = backoff.delayAfter(
                1,
                Optional.of(Duration.ofSeconds(45))
        );

        // Then
        assertThat(delay.delay()).isEqualTo(Duration.ofSeconds(45));
        assertThat(delay.retryAfterUsed()).isTrue();
    }

    @Test
    @DisplayName("Retry-After가 백오프와 같으면 백오프가 결정한 것으로 본다")
    void delayAfter_equalRetryAfter_isNotRetryAfterUsed() {
        // Given
        AiReviewRetryBackoff backoff = new AiReviewRetryBackoff(() -> 0.5);

        // When
        RetryDelay delay = backoff.delayAfter(
                1,
                Optional.of(Duration.ofSeconds(10))
        );

        // Then
        assertThat(delay.delay()).isEqualTo(Duration.ofSeconds(10));
        assertThat(delay.retryAfterUsed()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "90",
            "9223372036854775807"
    })
    @DisplayName("Retry-After가 60초를 넘으면 60초로 제한한다")
    void delayAfter_hugeRetryAfter_capsAtSixtySeconds(long retryAfterSeconds) {
        // Given
        AiReviewRetryBackoff backoff = new AiReviewRetryBackoff(() -> 0.5);

        // When
        RetryDelay delay = backoff.delayAfter(
                1,
                Optional.of(Duration.ofSeconds(retryAfterSeconds))
        );

        // Then
        assertThat(delay.delay()).isEqualTo(Duration.ofSeconds(60));
        assertThat(delay.retryAfterUsed()).isTrue();
    }
}
