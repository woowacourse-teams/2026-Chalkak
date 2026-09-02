package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationRetryPolicyTest {

    private final NotificationRetryPolicy retryPolicy = new NotificationRetryPolicy();

    @Test
    @DisplayName("재시도 지연은 30초부터 지수적으로 증가하고 10분을 넘지 않는다")
    void calculateDelay_increasingAttempts_appliesCappedExponentialBackoff() {
        // When & Then
        assertThat(retryPolicy.calculateDelay(1, null)).isEqualTo(Duration.ofSeconds(30));
        assertThat(retryPolicy.calculateDelay(2, null)).isEqualTo(Duration.ofMinutes(1));
        assertThat(retryPolicy.calculateDelay(3, null)).isEqualTo(Duration.ofMinutes(2));
        assertThat(retryPolicy.calculateDelay(10, null)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("Retry-After가 기본 백오프보다 길면 그 기간을 준수한다")
    void calculateDelay_longerRetryAfter_usesRetryAfter() {
        // When
        Duration delay = retryPolicy.calculateDelay(1, Duration.ofMinutes(3));

        // Then
        assertThat(delay).isEqualTo(Duration.ofMinutes(3));
    }

    @Test
    @DisplayName("Retry-After가 기본 백오프보다 짧으면 기본 백오프를 유지한다")
    void calculateDelay_shorterRetryAfter_keepsExponentialBackoff() {
        // When
        Duration delay = retryPolicy.calculateDelay(3, Duration.ofSeconds(10));

        // Then
        assertThat(delay).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    @DisplayName("비정상적으로 긴 Retry-After는 별도의 안전 상한으로 제한한다")
    void calculateDelay_retryAfterBeyondMaximum_capsAtMaximum() {
        // When
        Duration delay = retryPolicy.calculateDelay(1, Duration.ofDays(365));

        // Then
        assertThat(delay).isEqualTo(Duration.ofDays(1));
    }

    @Test
    @DisplayName("5번째 시도 전까지만 재시도한다")
    void canRetry_attemptBoundary_stopsAtFifthAttempt() {
        // When & Then
        assertThat(retryPolicy.canRetry(4)).isTrue();
        assertThat(retryPolicy.canRetry(5)).isFalse();
    }

    @Test
    @DisplayName("5번째까지는 실제 발송하지만 만료 임대를 6번째 선점하면 발송하지 않는다")
    void canAttempt_attemptBoundary_allowsFifthAndRejectsSixth() {
        // When & Then
        assertThat(retryPolicy.canAttempt(5)).isTrue();
        assertThat(retryPolicy.canAttempt(6)).isFalse();
    }

    @Test
    @DisplayName("배포 환경에서 주입한 최대 시도 횟수와 백오프 값을 적용한다")
    void configuredPolicy_customValues_appliesConfiguration() {
        // Given
        NotificationRetryPolicy configuredPolicy = new NotificationRetryPolicy(
                3,
                Duration.ofSeconds(5),
                Duration.ofSeconds(20)
        );

        // When & Then
        assertThat(configuredPolicy.canAttempt(3)).isTrue();
        assertThat(configuredPolicy.canAttempt(4)).isFalse();
        assertThat(configuredPolicy.calculateDelay(3, null)).isEqualTo(Duration.ofSeconds(20));
    }
}
