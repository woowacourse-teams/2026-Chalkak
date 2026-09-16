package com.chalkak.backend.feedback.api.v1.docs;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.exception.ErrorResponse;
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

@Tag(name = "Feedbacks", description = "사용자 피드백 API")
@SecurityRequirement(name = "accessToken")
public interface FeedbackApiDocs {

    @Operation(
            summary = "피드백 제출",
            description = "마이페이지에서 받은 자유 의견을 접수합니다. 이용이 정지된 회원도 제출할 수 있습니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "피드백 제출 성공",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "내용이 비었거나 1000자를 초과함",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 인증 정보 또는 탈퇴한 회원",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<FeedbackSubmissionResponse> submitFeedback(
            FeedbackSubmissionRequest request,
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );
}
