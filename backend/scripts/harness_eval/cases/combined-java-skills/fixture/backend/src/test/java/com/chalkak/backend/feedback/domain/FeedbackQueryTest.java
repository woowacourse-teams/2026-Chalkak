package com.chalkak.backend.feedback.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeedbackQueryTest {
  @Test
  @DisplayName("과거 조회 날짜를 그대로 반환한다")
  void getQueryDate_pastDate_returnsOriginalDate() {
    // Given
    Clock clock = Clock.fixed(Instant.parse("2026-10-08T15:00:00Z"), ZoneOffset.UTC);
    FeedbackQuery query = new FeedbackQuery(clock);
    LocalDate queryDate = LocalDate.of(2026, 10, 7);

    // When
    LocalDate result = query.getQueryDate(queryDate);

    // Then
    assertThat(result).isSameAs(queryDate);
  }
}
