package com.chalkak.backend.admin.api.v1.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "사용자 지표 수동 집계 요청")
public record AdminUserMetricsAggregationRequest(
        @NotNull
        @Schema(description = "집계할 KST 날짜. 오늘 이후는 집계할 수 없다", example = "2026-10-12")
        LocalDate metricDate
) {
}
