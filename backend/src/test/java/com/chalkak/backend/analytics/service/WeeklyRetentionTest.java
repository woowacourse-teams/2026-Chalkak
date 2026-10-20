package com.chalkak.backend.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeeklyRetentionTest {

    private static final LocalDate WEEK_START = LocalDate.of(
            2030,
            3,
            4
    );

    @Test
    @DisplayName("유지율은 소수 첫째 자리까지 반올림한다")
    void ratePercent_roundsHalfUpToOneDecimal() {
        // When & Then
        assertThat(new WeeklyRetention(
                1,
                WEEK_START,
                16,
                1
        ).ratePercent()).isEqualTo(6.3);
        assertThat(new WeeklyRetention(
                1,
                WEEK_START,
                3,
                2
        ).ratePercent()).isEqualTo(66.7);
        assertThat(new WeeklyRetention(
                1,
                WEEK_START,
                8,
                1
        ).ratePercent()).isEqualTo(12.5);
        assertThat(new WeeklyRetention(
                1,
                WEEK_START,
                3,
                1
        ).ratePercent()).isEqualTo(33.3);
        assertThat(new WeeklyRetention(
                1,
                WEEK_START,
                4,
                4
        ).ratePercent()).isEqualTo(100.0);
        assertThat(new WeeklyRetention(
                1,
                WEEK_START,
                4,
                0
        ).ratePercent()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("코호트가 비면 유지율은 null이다")
    void ratePercent_emptyCohort_isNull() {
        // When & Then
        assertThat(new WeeklyRetention(
                1,
                WEEK_START,
                0,
                0
        ).ratePercent()).isNull();
    }
}
