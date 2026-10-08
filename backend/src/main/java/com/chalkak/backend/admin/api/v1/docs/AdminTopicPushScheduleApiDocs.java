package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.AdminTopicPushScheduleCreationRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminTopicPushScheduleResponse;
import com.chalkak.backend.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Topic Push Schedules", description = "관리자 주제 푸시 발송 시각 설정")
public interface AdminTopicPushScheduleApiDocs {

    @Operation(summary = "주제 푸시 설정 생성", description = "KST 발송 시각 하나를 등록합니다. 같은 시각은 중복 등록할 수 없습니다. 등록된 설정은 등록 이후 도래하는 시각부터 적용하고, 지난 시각은 소급 실행하지 않습니다. 이 요청 자체는 푸시를 발송하지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "필수 값 누락, 잘못된 시각 또는 중복 시각", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminTopicPushScheduleResponse> createSchedule(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            AdminTopicPushScheduleCreationRequest request);
}
