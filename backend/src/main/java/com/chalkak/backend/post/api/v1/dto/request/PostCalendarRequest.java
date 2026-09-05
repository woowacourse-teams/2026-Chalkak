package com.chalkak.backend.post.api.v1.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.YearMonth;

public record PostCalendarRequest(
        @Schema(description = "조회 연도", example = "2026")
        @NotNull(message = "조회 연월이 올바르지 않습니다.")
        @Min(value = 1, message = "조회 연월이 올바르지 않습니다.")
        @Max(value = 9999, message = "조회 연월이 올바르지 않습니다.")
        Integer year,

        @Schema(description = "조회 월", example = "8")
        @NotNull(message = "조회 연월이 올바르지 않습니다.")
        @Min(value = 1, message = "조회 연월이 올바르지 않습니다.")
        @Max(value = 12, message = "조회 연월이 올바르지 않습니다.")
        Integer month
) {

    public YearMonth toYearMonth() {
        return YearMonth.of(year, month);
    }
}
