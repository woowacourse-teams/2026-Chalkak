package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.AdminAuditLogListRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminAuditLogListResponse;
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

@Tag(name = "Admin Audit Logs", description = "읽기 전용 관리자 감사 로그 API")
public interface AdminAuditLogApiDocs {

    @Operation(
            summary = "관리자 감사 로그 목록 조회",
            description = """
                    관리자·작업·대상·발생 시각으로 변경 이력을 조회합니다. 기간 양 끝값은 포함되고,
                    같은 발생 시각은 로그 ID로 안정 정렬합니다. 대상과 로그 사이에 외래 키를 두지 않아
                    삭제된 대상의 기록도 유지하며, 관리자 계정은 감사 로그가 있으면 삭제할 수 없습니다.
                    변경 전후 JSON은 AdminAction별 허용 필드 규칙을 따르고 비밀번호·토큰·Webhook·FCM 값과
                    이미지/스토리지 식별자를 저장하거나 응답하지 않습니다. 수정·삭제 API는 제공하지 않습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "감사 로그 목록 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 필터, 기간 또는 페이지 조건",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 API 접근 불가",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<AdminAuditLogListResponse> getAuditLogs(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @ParameterObject AdminAuditLogListRequest request
    );
}
