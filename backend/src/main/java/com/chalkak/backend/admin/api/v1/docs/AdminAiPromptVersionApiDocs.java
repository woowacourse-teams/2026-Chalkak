package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.AiPromptVersionCreateRequest;
import com.chalkak.backend.admin.api.v1.dto.request.AiPromptVersionListRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AiPromptVersionListResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AiPromptVersionResponse;
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

@Tag(name = "Admin AI Prompt Versions", description = "관리자 AI 프롬프트 버전")
public interface AdminAiPromptVersionApiDocs {

    @Operation(
            summary = "AI 프롬프트 버전 생성",
            description = "프롬프트 버전을 새로 만듭니다. 버전 번호는 type별로 1부터 서버가 부여하며, 만든 버전은 수정하거나 삭제할 수 없습니다. "
                    + "같은 type에서 가장 큰 버전이 최신이고 사용자 기능이 이 최신 버전을 사용하므로, 만들면 곧바로 사용자 기능에 적용됩니다. "
                    + "저장 전에 미리보기 API로 초안을 먼저 확인하세요. "
                    + "시스템 프롬프트에는 변수를 쓸 수 없고, 사용자 프롬프트 템플릿에는 type이 허용한 변수만 쓸 수 있습니다. "
                    + "감사 로그에는 프롬프트 원문 없이 type과 version만 남깁니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성된 프롬프트 버전", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "필수 값 누락·길이 초과, 잘못된 열거형 값, 프롬프트·옵션 검증 실패(허용되지 않은 변수 포함), 허용되지 않은 모델", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AiPromptVersionResponse> createVersion(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            AiPromptVersionCreateRequest request);

    @Operation(
            summary = "AI 프롬프트 버전 목록 조회",
            description = "type 하나의 프롬프트 버전을 version 내림차순(최신 먼저)으로 조회합니다. "
                    + "같은 type에서 가장 큰 버전이 최신이며 사용자 기능이 사용하는 버전입니다. "
                    + "목록에는 프롬프트 본문과 모델 옵션이 없으므로 본문은 상세 조회로 확인하세요. "
                    + "page는 1부터, pageSize는 1~100이며 생략하면 각각 1, 20입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "프롬프트 버전 목록과 페이지 정보", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "type 누락·잘못된 값, page·pageSize 형식 오류 또는 범위 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AiPromptVersionListResponse> getVersions(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @ParameterObject AiPromptVersionListRequest request);

    @Operation(
            summary = "AI 프롬프트 버전 상세 조회",
            description = "프롬프트 버전 하나를 시스템 프롬프트·사용자 프롬프트 템플릿·모델 옵션까지 전체 조회합니다. "
                    + "이전 버전의 내용을 바탕으로 새 버전을 만들 때 사용합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "프롬프트 버전 전체", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "promptVersionId가 UUID 형식이 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "프롬프트 버전을 찾을 수 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AiPromptVersionResponse> getVersion(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @Parameter(description = "프롬프트 버전 ID", schema = @Schema(type = "string", format = "uuid")) String promptVersionId);
}
