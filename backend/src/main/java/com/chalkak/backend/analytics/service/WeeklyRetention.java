package com.chalkak.backend.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 기준 주보다 {@code weeksAfter}주 앞선 코호트 주({@code cohortWeekStart}부터 7일)의 게시물 작성 회원
 * 중, 기준 주에도 게시물을 올린 회원 수.
 */
public record WeeklyRetention(
        int weeksAfter,
        LocalDate cohortWeekStart,
        long cohortSize,
        long retainedUsers
) {

    /** 유지율(%)을 소수 첫째 자리까지 반올림한 값. 코호트가 비면 {@code null}. */
    public Double ratePercent() {
        if (cohortSize == 0) {
            return null;
        }
        return BigDecimal.valueOf(retainedUsers * 100)
                .divide(
                        BigDecimal.valueOf(cohortSize),
                        1,
                        RoundingMode.HALF_UP
                )
                .doubleValue();
    }
}
