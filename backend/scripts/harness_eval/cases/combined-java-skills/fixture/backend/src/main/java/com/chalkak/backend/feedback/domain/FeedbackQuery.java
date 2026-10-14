package com.chalkak.backend.feedback.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;

public class FeedbackQuery {
  private final Clock clock;

  public FeedbackQuery(Clock clock) {
    this.clock = clock;
  }

  public LocalDate getQueryDate(LocalDate queryDate) {
    if (queryDate.isAfter(LocalDate.now(clock))) {
      throw new BusinessException(ErrorCode.BUSINESS_ERROR, "미래 날짜는 조회할 수 없습니다.");
    }
    return queryDate;
  }
}
