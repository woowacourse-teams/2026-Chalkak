package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.AdminAiReviewListRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminAiReviewDetailResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminAiReviewListResponse;
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

@Tag(name = "Admin AI Reviews", description = "관리자 AI 리뷰 조회")
public interface AdminAiReviewApiDocs {

    @Operation(summary = "AI 리뷰 목록 조회", description = "전체 AI 리뷰를 상태와 프롬프트 버전으로 걸러 마지막 요청 시각 내림차순(같으면 ID 내림차순)으로 조회합니다. "
            + "사용자 앱에서 보이지 않는 숨겨진 리뷰(게시물 삭제·미승인, 주제·사진 삭제, 작성자 탈퇴)도 거르지 않고 포함하며, "
            + "게시물과 작성자의 원값(deletedAt, status)을 그대로 줍니다. "
            + "visibleToAuthor는 작성자 앱에서 이 리뷰가 보이는지를 뜻합니다. 게시물이 승인됐고 게시물·주제·사진·작성자가 모두 삭제되지 않았을 때만 true이며, 정지된 작성자는 영향을 주지 않습니다. "
            + "한 게시물에 리뷰는 하나이고 다시 요청하면 같은 항목이 새 프롬프트 버전으로 바뀝니다. 목록의 promptVersion은 이번 회차에 쓴 버전입니다. "
            + "overallScore는 완료된 리뷰만 값이 있고, 사용량과 지연시간은 마지막 호출의 값이라 호출 기록이 없으면 null입니다. "
            + "존재하지 않는 promptVersionId로 조회하면 404가 아니라 빈 목록을 돌려줍니다. "
            + "page는 1부터, pageSize는 1~100이며 생략하면 각각 1, 20입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI 리뷰 목록과 페이지 정보", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "status·promptVersionId 형식 오류, page·pageSize 형식 오류 또는 범위 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminAiReviewListResponse> getAiReviews(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @ParameterObject AdminAiReviewListRequest request);

    @Operation(summary = "AI 리뷰 상세 조회", description = "AI 리뷰 하나를 목록 항목의 모든 값과 함께 결과(result)·모델 원출력(output)·OpenAI 응답 ID·시도 횟수·시작 시각·원본 사진 URL까지 조회합니다. "
            + "사용자 앱에서 보이지 않는 숨겨진 리뷰(게시물 삭제·미승인, 주제·사진 삭제, 작성자 탈퇴)도 거르지 않고 포함하며, 게시물과 작성자의 원값(deletedAt, status)을 그대로 줍니다. "
            + "visibleToAuthor는 작성자 앱에서 이 리뷰가 보이는지를 뜻합니다. 게시물이 승인됐고 게시물·주제·사진·작성자가 모두 삭제되지 않았을 때만 true이며, 정지된 작성자는 영향을 주지 않습니다. "
            + "result는 서비스 규칙으로 변환한 결과이며 완료된 리뷰만 값이 있습니다. "
            + "output은 모델이 돌려준 원출력을 문자열이 아닌 JSON 객체로 그대로 담으며 구도 점수(compositionScore)와 주제 표현 점수(themeExpressionScore)의 원점수와 피드백이 들어 있습니다. 원출력이 없으면 null입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI 리뷰 상세", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "aiReviewId가 UUID 형식이 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "AI 리뷰를 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminAiReviewDetailResponse> getAiReview(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @Parameter(description = "AI 리뷰 ID", schema = @Schema(type = "string", format = "uuid")) String aiReviewId);
}
