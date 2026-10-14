package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.AdminNoticeDeletionRequest;
import com.chalkak.backend.admin.api.v1.dto.request.AdminNoticeListRequest;
import com.chalkak.backend.admin.api.v1.dto.request.AdminNoticeRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminNoticeListResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminNoticeResponse;
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

@Tag(name = "Admin Notices", description = "관리자 공지 관리")
public interface AdminNoticeApiDocs {

    @Operation(summary = "공지 목록 조회", description = "등록한 공지를 등록 최신순으로 페이지 단위로 조회합니다. 공지가 없으면 빈 목록을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 페이지 조건", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminNoticeListResponse> getNotices(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @ParameterObject AdminNoticeListRequest request);

    @Operation(summary = "공지 상세 조회", description = "공지 한 건을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 ID", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "공지 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminNoticeResponse> getNotice(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @Parameter(description = "공지 ID", required = true, schema = @Schema(type = "string", format = "uuid")) String noticeId);

    @Operation(summary = "공지 등록", description = "공지를 예약 시각과 함께 등록합니다. 즉시 발송은 현재 시각을 예약 시각으로 전달합니다. 이 요청 자체는 푸시를 발송하지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "필수 값 누락, 제목·본문 길이 초과, 5분 넘게 과거인 예약 시각 또는 감사 로그에 저장할 수 없는 민감 정보 포함", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminNoticeResponse> createNotice(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            AdminNoticeRequest request);

    @Operation(summary = "공지 수정", description = "배포 전 공지의 제목·본문·예약 시각을 수정합니다. 검증 기준은 등록과 같습니다. 배포된 공지는 수정할 수 없으며, 같은 내용으로 수정하면 변경 없이 현재 공지를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 ID, 필수 값 누락, 제목·본문 길이 초과, 5분 넘게 과거인 예약 시각, 감사 로그에 저장할 수 없는 민감 정보 포함 또는 배포된 공지", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "공지 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminNoticeResponse> updateNotice(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @Parameter(description = "공지 ID", required = true, schema = @Schema(type = "string", format = "uuid")) String noticeId,
            AdminNoticeRequest request);

    @Operation(summary = "공지 삭제", description = "사유와 함께 공지를 삭제합니다. 배포 전 공지는 발송이 취소되고, 배포된 공지는 회원 알림함에서 숨기며 아직 보내지 않은 푸시도 보내지 않습니다. 이미 기기에 표시된 푸시는 회수하지 않습니다. 이미 삭제한 공지를 다시 삭제하면 최초 결과를 유지합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 ID, 사유 누락·공백 또는 500자 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "공지 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> deleteNotice(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @Parameter(description = "공지 ID", required = true, schema = @Schema(type = "string", format = "uuid")) String noticeId,
            AdminNoticeDeletionRequest request);
}
