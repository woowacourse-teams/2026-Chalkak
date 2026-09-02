package com.chalkak.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignatureProcessingPolicyTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-26T00:00:00Z");

    @Test
    @DisplayName("대기 시간을 넘기면 사인 처리가 타임아웃된 것으로 본다")
    void isProcessingTimedOut_afterTimeout_returnsTrue() {
        // Given
        SignatureProcessingPolicy policy =
                new SignatureProcessingPolicy(Duration.ofMinutes(7));

        // When & Then
        assertThat(policy.isProcessingTimedOut(
                STARTED_AT,
                STARTED_AT.plusSeconds(421)))
                .isTrue();
    }

    @Test
    @DisplayName("대기 시간 경계에서는 사인 처리를 계속 기다린다")
    void isProcessingTimedOut_atTimeout_returnsFalse() {
        // Given
        SignatureProcessingPolicy policy =
                new SignatureProcessingPolicy(Duration.ofMinutes(7));

        // When & Then
        assertThat(policy.isProcessingTimedOut(
                STARTED_AT,
                STARTED_AT.plusSeconds(420)))
                .isFalse();
    }

    @Test
    @DisplayName("대기 시간 직전에는 사인 처리를 계속 기다린다")
    void isProcessingTimedOut_beforeTimeout_returnsFalse() {
        // Given
        SignatureProcessingPolicy policy =
                new SignatureProcessingPolicy(Duration.ofMinutes(7));

        // When & Then
        assertThat(policy.isProcessingTimedOut(
                STARTED_AT,
                STARTED_AT.plusSeconds(419)))
                .isFalse();
    }

    @Test
    @DisplayName("사인 처리 제한 시간은 0보다 커야 한다")
    void constructor_nonPositiveTimeout_throwsException() {
        // When & Then
        assertThatThrownBy(() -> new SignatureProcessingPolicy(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SignatureProcessingPolicy(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
