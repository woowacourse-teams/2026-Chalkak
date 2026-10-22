package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.AiPromptPreviewRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AiPromptPreviewResponse;
import com.chalkak.backend.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin AI Prompt Previews", description = "관리자 AI 프롬프트 미리보기")
public interface AdminAiPromptPreviewApiDocs {

    @Operation(summary = "AI 프롬프트 미리보기 실행", description = "저장하지 않은 프롬프트 초안을 공개(APPROVED, 삭제되지 않은) 게시물의 원본 이미지와 주제 제목으로 실행해 결과를 돌려줍니다. "
            + "결과는 저장하거나 사용자에게 보여주지 않으며 감사 로그도 남기지 않습니다. "
            + "요청이 유효하면 OpenAI 호출 실패(시간 초과, 한도 소진, 거절, 출력 오류 등)도 오류가 아닌 200 결과이며 succeeded=false와 failureType으로 구분합니다. "
            + "사용량·지연시간·응답 ID는 OpenAI가 응답 본문을 준 경우에만 값이 있습니다. "
            + "템플릿 변수 값은 항상 JSON 문자열 규칙으로 이스케이프해 넣습니다(예: {\"주제\": \"{{topicTitle}}\"}가 유효한 JSON으로 유지되며, 평문 템플릿에서도 따옴표는 \\\"로 들어갑니다).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "실행 결과(성공 또는 OpenAI 실패 결과)", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "필수 값 누락·길이 초과, 잘못된 게시물 ID·열거형 값, 프롬프트·옵션 검증 실패(허용되지 않은 변수 포함), 허용되지 않은 모델", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "게시물이 없거나 공개 상태가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AiPromptPreviewResponse> preview(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            AiPromptPreviewRequest request);
}
