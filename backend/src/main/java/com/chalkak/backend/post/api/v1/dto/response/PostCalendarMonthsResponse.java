package com.chalkak.backend.post.api.v1.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.YearMonth;
import java.util.List;

public record PostCalendarMonthsResponse(
        @Schema(description = "게시물이 있는 연월 목록. 중복 없이 최신순이며, 기록이 없으면 빈 배열") List<CalendarMonthResponse> months) {

    public static PostCalendarMonthsResponse fromYearMonths(List<YearMonth> months) {
        return new PostCalendarMonthsResponse(months.stream()
                .map(month -> new CalendarMonthResponse(month.getYear(), month.getMonthValue()))
                .toList());
    }

    public record CalendarMonthResponse(int year, int month) {
    }
}
