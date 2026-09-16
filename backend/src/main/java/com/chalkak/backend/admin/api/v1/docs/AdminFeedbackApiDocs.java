package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.AdminFeedbackListRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminFeedbackListResponse;
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

    @Operation(
            summary = "관리자 피드백 목록 조회",
            description = "접수된 사용자 피드백을 작성자 정보와 함께 최신순으로 조회합니다. "
                    + "탈퇴한 회원이 남긴 피드백도 함께 조회됩니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "관리자 피드백 목록 조회 성공",
                    useReturnTypeSchema = true),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 정렬 또는 페이지 조건",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 API 접근 불가",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminFeedbackListResponse> getFeedbacks(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @ParameterObject AdminFeedbackListRequest request
    );
}
