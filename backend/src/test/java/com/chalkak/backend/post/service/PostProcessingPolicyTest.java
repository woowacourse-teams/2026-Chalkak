package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PostProcessingPolicyTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-20T00:00:00Z");

    @Test
    @DisplayName("대기 시간을 넘기면 처리가 끝나지 않은 것으로 본다")
    void isProcessingTimedOut_afterTimeout_returnsTrue() {
        // Given
        PostProcessingPolicy policy = new PostProcessingPolicy(Duration.ofMinutes(7));

        // When & Then
        assertThat(policy.isProcessingTimedOut(STARTED_AT, STARTED_AT.plusSeconds(421)))
                .isTrue();
    }

    @Test
    @DisplayName("대기 시간 경계에서는 아직 기다린다")
    void isProcessingTimedOut_atTimeout_returnsFalse() {
        // Given
        PostProcessingPolicy policy = new PostProcessingPolicy(Duration.ofMinutes(7));

        // When & Then
        assertThat(policy.isProcessingTimedOut(STARTED_AT, STARTED_AT.plusSeconds(420)))
                .isFalse();
    }

    @Test
    @DisplayName("0 이하의 대기 시간은 설정할 수 없다")
    void constructor_nonPositiveTimeout_throwsIllegalArgumentException() {
        // When & Then
        assertThatThrownBy(() -> new PostProcessingPolicy(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PostProcessingPolicy(Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
