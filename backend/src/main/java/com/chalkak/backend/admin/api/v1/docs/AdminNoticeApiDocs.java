package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.AdminNoticeRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminNoticeResponse;
import com.chalkak.backend.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Notices", description = "관리자 공지 관리")
public interface AdminNoticeApiDocs {

    @Operation(summary = "공지 등록", description = "공지를 예약 시각과 함께 등록합니다. 즉시 발송은 현재 시각을 예약 시각으로 전달합니다. 이 요청 자체는 푸시를 발송하지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "필수 값 누락, 제목·본문 길이 초과, 5분 넘게 과거인 예약 시각 또는 감사 로그에 저장할 수 없는 민감 정보 포함", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminNoticeResponse> createNotice(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            AdminNoticeRequest request);
}
