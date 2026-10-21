package com.chalkak.backend.aireview.api.v1.docs;

import com.chalkak.backend.aireview.api.v1.dto.response.PostAiReviewResponse;
import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.exception.ErrorResponse;
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

@Tag(name = "Post AI Reviews", description = "게시물 AI 리뷰 API")
@SecurityRequirement(name = "accessToken")
public interface PostAiReviewApiDocs {

    @Operation(summary = "게시물 AI 리뷰 요청", description = """
            본인 게시물의 AI 리뷰를 요청한다. 게시물당 리뷰는 하나이며, 이미 요청했다면 새로 만들지 않고 현재 상태를 그대로 응답한다.

            앱은 HTTP 상태 코드가 아니라 응답의 `status`로 분기한다. 2xx는 모두 정상 응답이며 202는 아직 끝나지 않았다는 뜻이다.
            - `PROCESSING`: 대기 중이거나 처리 중이다. 202로 응답하며 `GET`으로 상태를 확인한다. 일시적인 실패는 서버가 자동으로 다시 시도하는 동안에도 `PROCESSING`이다.
            - `COMPLETED`: 완료됐다. 200으로 응답하며 `result`가 있다.
            - `FAILED`: 실패했다. 200으로 응답하며 실패 사유는 노출하지 않는다.

            실패(`FAILED`)한 리뷰에 다시 POST하면 재요청으로 처리한다. 리뷰를 대기열에 다시 넣고 202 `PROCESSING`으로 응답하며, 같은 POST를 한 번 더 보내도 이미 처리 중이므로 요청 횟수는 늘지 않는다. 요청은 처음 요청을 포함해 게시물당 최대 3번(재요청은 최대 2번)이며, 4번째 요청은 400(`BUSINESS_ERROR`)이고 `message`는 "더 이상 AI 리뷰를 다시 요청할 수 없습니다."이다. 응답의 `canRequestAgain`이 true일 때만 "다시 요청" 버튼을 보여 준다.

            쓸 수 있는 AI 설정이 없으면 새로 요청하거나 다시 요청할 수 없고 400으로 응답한다. 이미 처리 중이거나 완료된 리뷰는 이 경우에도 기존 상태를 응답한다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "이미 완료됐거나, 실패했지만 다시 요청할 수 없는 리뷰의 현재 상태", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "202", description = "요청 또는 재요청을 접수했거나 아직 처리 중이다. `status`는 PROCESSING이다.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = PostAiReviewResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 게시물 ID이거나, 지금은 AI 리뷰를 요청할 수 없는 상태이거나, 재요청 횟수(3번)를 모두 쓴 상태(`message`로 안내)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "이용이 정지된 회원", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "본인의 공개된 게시물이 아님(없거나 남의 게시물이거나 공개 조건을 만족하지 않음)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PostAiReviewResponse> requestAiReview(
            @Parameter(description = "게시물 ID", example = "0198f6c1-62ba-7d30-8b12-0f733b6570d4") String postId,
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );

    @Operation(summary = "게시물 AI 리뷰 조회", description = """
            본인 게시물의 AI 리뷰 상태와 결과를 조회한다. 이용이 정지된 회원도 조회할 수 있다.

            앱은 응답의 `status`로 분기하며 실패 사유는 노출하지 않는다. 모두 200으로 응답한다.
            - `NONE`: 아직 요청하지 않았다. `requestedAt`, `completedAt`, `result`가 null이며 "AI 피드백 받기"를 보여 준다.
            - `PROCESSING`: 대기 중이거나 처리 중이다.
            - `COMPLETED`: 완료됐다. `result`가 있다.
            - `FAILED`: 실패했다. `canRequestAgain`이 true이면 다시 요청할 수 있다.

            `canRequestAgain`은 `FAILED`이고 요청 횟수(처음 포함 최대 3번)가 남았을 때만 true이며 그 밖의 상태는 false이다. 횟수만 반영하므로 true여도 AI 설정이 없으면 다시 요청이 400, 이용이 정지된 회원이면 403일 수 있다.

            404는 게시물 문제일 때만 응답한다. 요청하지 않은 것은 404가 아니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI 리뷰의 현재 상태. 요청 전(`NONE`)이나 진행 중이어도 200이다.", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 게시물 ID", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "본인의 공개된 게시물이 아님(없거나 남의 게시물이거나 공개 조건을 만족하지 않음)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PostAiReviewResponse> getAiReview(
            @Parameter(description = "게시물 ID", example = "0198f6c1-62ba-7d30-8b12-0f733b6570d4") String postId,
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );
}
