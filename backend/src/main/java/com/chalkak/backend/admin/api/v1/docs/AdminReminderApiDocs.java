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
            @ApiResponse(responseCode = "400", description = "필수 값 누락, 제목·본문 길이 초과, 잘못된 반복 방식·대상, 과거인 발송 시각 또는 감사 로그에 저장할 수 없는 민감 정보 포함", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminReminderResponse> createReminder(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            AdminReminderRequest request);

    @Operation(summary = "리마인더 수정", description = "리마인더의 제목·본문·반복 방식·대상·발송 시각을 수정합니다. 일회성은 발송 시각 전에만 수정할 수 있고, 매일 반복은 언제든 수정할 수 있으며 다음 회차부터 적용됩니다. 발송 시각이나 반복 방식을 바꿀 때만 과거인 시각을 거절합니다. 같은 내용으로 수정하면 변경 없이 현재 리마인더를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 ID, 필수 값 누락, 제목·본문 길이 초과, 잘못된 반복 방식·대상, 바꾼 발송 시각이 과거, 발송 시각이 지난 일회성 리마인더 또는 감사 로그에 저장할 수 없는 민감 정보 포함", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "리마인더 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminReminderResponse> updateReminder(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @Parameter(description = "리마인더 ID", required = true, schema = @Schema(type = "string", format = "uuid")) String reminderId,
            AdminReminderRequest request
    );

    @Operation(summary = "리마인더 삭제", description = "리마인더를 삭제합니다. 삭제한 리마인더는 목록에서 빠지고 아직 실행하지 않은 회차는 보내지 않습니다. 이미 기기에 표시된 푸시는 회수하지 않습니다. 이미 삭제한 리마인더를 다시 삭제하면 최초 결과를 유지합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 ID", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "리마인더 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> deleteReminder(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @Parameter(description = "리마인더 ID", required = true, schema = @Schema(type = "string", format = "uuid")) String reminderId
    );
}
