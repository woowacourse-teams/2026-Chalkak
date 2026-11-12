package com.chalkak.backend.filter.api.v1.docs;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.exception.ErrorResponse;
import com.chalkak.backend.filter.api.v1.dto.response.FilterDraftResponse;
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

@Tag(name = "Filters", description = "필터 API")
@SecurityRequirement(name = "accessToken")
public interface FilterApiDocs {

    @Operation(summary = "필터 초안 생성", description = "이용 가능한 작가가 빈 필터 초안을 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "필터 초안 생성 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "작가 권한 없음, 이용 정지 또는 일반 사용자 API에 접근할 수 없는 토큰", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<FilterDraftResponse> createDraft(
            @Parameter(hidden = true) AuthenticatedUser loginUser);
}
