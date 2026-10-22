package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.AdminUserMetricsAggregationRequest;
import com.chalkak.backend.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Analytics", description = "관리자 사용자 지표 API")
public interface AdminAnalyticsApiDocs {

    @Operation(summary = "사용자 지표 수동 집계", description = "집계 로그를 다시 남기는 요청이다. 과거 그래프의 값은 바뀌지 않는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "백그라운드 집계 시작"),
            @ApiResponse(responseCode = "400", description = "날짜 누락·형식 오류, 오늘 이후 날짜 또는 이미 진행 중인 집계(errorCode USER_METRICS_AGGREGATION_IN_PROGRESS)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> startUserMetricsAggregation(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            AdminUserMetricsAggregationRequest request);
}
