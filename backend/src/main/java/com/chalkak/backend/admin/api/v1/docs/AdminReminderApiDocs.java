package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.AdminReminderListRequest;
import com.chalkak.backend.admin.api.v1.dto.request.AdminReminderRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminReminderListResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminReminderResponse;
import com.chalkak.backend.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Reminders", description = "관리자 리마인더 관리")
public interface AdminReminderApiDocs {

    @Operation(summary = "리마인더 목록 조회", description = "등록한 리마인더를 등록 최신순으로 페이지 단위로 조회합니다. 리마인더가 없으면 빈 목록을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 페이지 조건", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminReminderListResponse> getReminders(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @ParameterObject AdminReminderListRequest request);

    @Operation(summary = "리마인더 등록", description = "제목·본문·반복 방식·대상·발송 시각으로 리마인더를 등록합니다. 리마인더는 알림함에 남지 않고 푸시로만 보내며, 이 요청 자체는 푸시를 발송하지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "필수 값 누락, 제목·본문 길이 초과, 잘못된 반복 방식·대상, 5분 넘게 과거인 발송 시각 또는 감사 로그에 저장할 수 없는 민감 정보 포함", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminReminderResponse> createReminder(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            AdminReminderRequest request);
}
