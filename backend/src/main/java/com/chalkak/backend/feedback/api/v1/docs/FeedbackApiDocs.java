package com.chalkak.backend.feedback.api.v1.docs;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.exception.ErrorResponse;
import com.chalkak.backend.feedback.api.v1.dto.request.FeedbackListRequest;
import com.chalkak.backend.feedback.api.v1.dto.response.FeedbackListResponse;
import com.chalkak.backend.feedback.api.v1.dto.response.FeedbackDetailResponse;
import com.chalkak.backend.feedback.api.v1.dto.request.FeedbackSubmissionRequest;
import com.chalkak.backend.feedback.api.v1.dto.response.FeedbackSubmissionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springdoc.core.annotations.ParameterObject;

@Tag(name = "Feedbacks", description = "사용자 피드백 API")
@SecurityRequirement(name = "accessToken")
public interface FeedbackApiDocs {

    @Operation(summary = "본인 문의 상세 조회", description = "로그인한 회원의 삭제되지 않은 문의를 조회합니다. 정지 회원도 조회할 수 있습니다. 타인의 문의, 삭제된 문의, 존재하지 않는 문의는 구분하지 않고 같은 404 응답으로 처리합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "본인 문의 상세 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "문의 식별자 형식이 올바르지 않음", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보 또는 탈퇴한 회원", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "조회할 수 있는 본인 문의가 없음", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<FeedbackDetailResponse> getFeedback(
            @Parameter(schema = @Schema(type = "string", format = "uuid")) String feedbackId,
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );

    @Operation(summary = "본인 문의 목록 조회", description = "로그인한 회원의 삭제되지 않은 문의만 접수 시각·식별자 최신순으로 조회합니다. 도입 전 피드백은 소프트 딜리트하여 목록에서 제외합니다. 정지 회원도 조회할 수 있습니다. 페이지는 1부터 시작하고 기본 20건, 최대 100건입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "본인 문의 목록 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "조회 조건이 올바르지 않음", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보 또는 탈퇴한 회원", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<FeedbackListResponse> getFeedbacks(
            @ParameterObject FeedbackListRequest request,
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );

    @Operation(summary = "피드백 제출", description = "마이페이지에서 받은 문의를 접수합니다. 정지 회원도 제출할 수 있습니다. 회원별 1분에 1건, 최근 24시간 최대 5건이며 성공 접수만 집계합니다. 제한 초과 시 400 BUSINESS_ERROR 메시지에 다음 접수 가능 시각을 UTC Instant로 안내합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "피드백 제출 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "내용이 비었거나 1000자를 초과함, 또는 접수 간격·횟수 제한 초과", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보 또는 탈퇴한 회원", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<FeedbackSubmissionResponse> submitFeedback(
            FeedbackSubmissionRequest request,
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );
}
