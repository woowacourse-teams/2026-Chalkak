package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.AdminFeedbackListRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminFeedbackListResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminFeedbackDetailResponse;
import com.chalkak.backend.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Feedbacks", description = "관리자 피드백 조회 API")
public interface AdminFeedbackApiDocs {

    @Operation(summary = "관리자 문의 상세 조회", description = "문의 ID로 내용·접수 시각·삭제 시각과 작성자 정보를 조회합니다. "
            + "삭제된 문의와 정지·탈퇴 회원이 남긴 문의도 포함하며, 없는 문의는 404로 처리합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "관리자 문의 상세 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "문의 식별자가 잘못되거나 축약된 UUID", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "관리자 인증 정보가 없거나 유효하지 않음", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "문의가 존재하지 않음", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminFeedbackDetailResponse> getFeedback(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @Parameter(schema = @Schema(type = "string", format = "uuid")) String feedbackId
    );

    @Operation(summary = "관리자 피드백 목록 조회", description = "접수된 문의를 작성자 정보·문의 삭제 시각과 함께 조회합니다. "
            + "userId를 생략하면 전체, 전달하면 해당 사용자의 문의만 조회하며 정지·탈퇴 회원과 삭제된 문의도 포함합니다. "
            + "해당 사용자의 문의가 없으면 빈 목록을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "관리자 피드백 목록 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못되거나 축약된 사용자 UUID, 정렬 또는 페이지 조건", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "관리자 인증 정보가 없거나 유효하지 않음", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminFeedbackListResponse> getFeedbacks(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @ParameterObject AdminFeedbackListRequest request
    );
}
