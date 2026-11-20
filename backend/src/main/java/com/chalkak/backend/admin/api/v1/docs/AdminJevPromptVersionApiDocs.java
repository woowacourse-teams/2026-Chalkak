package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.JevPromptActivationRequest;
import com.chalkak.backend.admin.api.v1.dto.request.JevPromptVersionCreateRequest;
import com.chalkak.backend.admin.api.v1.dto.response.JevPromptVersionListResponse;
import com.chalkak.backend.admin.api.v1.dto.response.JevPromptVersionResponse;
import com.chalkak.backend.jev.domain.JevPromptRole;
import com.chalkak.backend.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Jev Prompts", description = "Jev 전용 수동 프롬프트 저장·운영 적용")
public interface AdminJevPromptVersionApiDocs {

    @Operation(summary = "Jev 프롬프트 버전 저장", description = """
            관리자 입력만 저장하며 저장 자체로 운영 적용하지 않습니다. GPT 모델·옵션과 프롬프트를 받지 않습니다.
            분류는 topic_category Choice와 general 선택지가 필요합니다.
            사진 검수는 topic_fit·image_safety·image_expression의 세 Choice와 승인/검토/반려 선택지가 필요합니다.
            categoryRules에는 general 기본 규칙이 필요합니다. categoryInstructions는 특정 카테고리의 주제 공통 지침을 교체합니다.
            모델은 jev-1.14.0으로 고정해 별칭의 변경을 방지합니다. 버전 내용은 수정하지 않고 새 버전으로 저장합니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "비활성 프롬프트 버전 저장", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 용도·모델·질문·옵션", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<JevPromptVersionResponse> createVersion(
            @Parameter(hidden = true) AuthenticatedAdmin admin,
            JevPromptVersionCreateRequest request
    );

    @Operation(summary = "Jev 프롬프트 버전 목록 조회", description = "용도별 최신 버전순으로 조회합니다. 목록에는 프롬프트 전문이 없습니다. page는 1부터, pageSize는 1~100입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "목록 조회", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 용도·조회 범위", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<JevPromptVersionListResponse> getVersions(
            @Parameter(hidden = true) AuthenticatedAdmin admin,
            JevPromptRole role,
            int page,
            int pageSize
    );

    @Operation(summary = "Jev 프롬프트 버전 상세 조회", description = "GPT 프롬프트 ID는 이 경로에서 조회되지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "버전 상세 조회", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 UUID", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Jev 버전 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<JevPromptVersionResponse> getVersion(
            @Parameter(hidden = true) AuthenticatedAdmin admin,
            String promptVersionId
    );

    @Operation(summary = "Jev 프롬프트 운영 적용·비활성화", description = """
            active=true로 명시 적용하면 같은 용도의 이전 활성 버전을 교체합니다. 저장된 본문은 변경하지 않습니다.
            active=false는 지정한 버전이 현재 활성 버전일 때만 비활성화합니다. 동일 상태의 재요청은 감사 로그를 추가하지 않습니다.
            다른 용도와 GPT 설정에는 영향을 주지 않습니다. 분류/사진 필수 프롬프트가 모두 적용돼야 후속 자동 검수에 사용할 수 있습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "적용 상태 변경", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 입력·호환되지 않는 카테고리 규칙", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "관리자 인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Jev 버전 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<JevPromptVersionResponse> updateActivation(
            @Parameter(hidden = true) AuthenticatedAdmin admin,
            String promptVersionId,
            JevPromptActivationRequest request
    );
}
