package com.chalkak.backend.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReminderRepeatTest {

    private static final Instant SCHEDULED_AT = Instant.parse("2026-10-21T12:00:00Z");

    @ParameterizedTest
    @CsvSource({"-1, true", "0, false", "1, false"})
    @DisplayName("일회성은 발송 시각 직전까지만 수정할 수 있고 발송 시각부터는 수정할 수 없다")
    void canUpdate_once_allowsOnlyBeforeScheduledAt(long offsetNanos, boolean expected) {
        // When & Then
        assertThat(ReminderRepeat.ONCE.canUpdate(SCHEDULED_AT, SCHEDULED_AT.plusNanos(offsetNanos)))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("매일 반복은 첫 발송 시각이 지난 뒤에도 수정할 수 있다")
    void canUpdate_daily_alwaysAllows() {
        // When & Then
        assertThat(ReminderRepeat.DAILY.canUpdate(SCHEDULED_AT, SCHEDULED_AT.minusSeconds(1)))
                .isTrue();
        assertThat(
                ReminderRepeat.DAILY.canUpdate(SCHEDULED_AT, SCHEDULED_AT.plusSeconds(86_400 * 30)))
                .isTrue();
    }
}
