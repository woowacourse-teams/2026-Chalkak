package com.chalkak.backend.filter.api.v1.docs;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.exception.ErrorResponse;
import com.chalkak.backend.filter.api.v1.dto.request.AuthorFilterListRequest;
import com.chalkak.backend.filter.api.v1.dto.response.AuthorFilterListResponse;
import com.chalkak.backend.filter.api.v1.dto.response.FilterDetailResponse;
import com.chalkak.backend.filter.api.v1.dto.response.FilterDraftResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
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

    @Operation(summary = "작성자의 필터 목록 조회", description = """
            공개 필터를 생성 최신순으로 반환합니다. 본인 목록에는 초안과 비공개 필터도 포함됩니다.
            정지 회원도 조회할 수 있으며, 표시할 필터가 없으면 빈 목록을 반환합니다.
            page는 1부터 시작하고 기본 pageSize는 20입니다. hasNext가 true이면 다음 페이지를 조회합니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "필터 목록 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 회원 ID 또는 페이지 조건", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "일반 사용자 API에 접근할 수 없는 토큰", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AuthorFilterListResponse> getAuthorFilters(
            @Parameter(name = "userId", description = "필터를 만든 회원 ID", schema = @Schema(type = "string", format = "uuid")) String artistId,
            @ParameterObject AuthorFilterListRequest request,
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );

    @Operation(summary = "필터 상세 조회", description = """
            공개 필터는 로그인한 회원이, 초안과 비공개 필터는 작성자 본인이 조회할 수 있습니다.
            이름·설명·상태와 소개 사진을 표시 순서대로 반환합니다. 다른 회원에게는 READY 사진만 제공하며,
            작성자 본인에게는 처리 중이거나 반려된 사진의 상태도 제공합니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "필터 상세 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 필터 ID 형식", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "일반 사용자 API에 접근할 수 없는 토큰", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "필터가 없거나 조회할 수 없음", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<FilterDetailResponse> getFilter(
            @Parameter(description = "필터 ID", schema = @Schema(type = "string", format = "uuid")) String filterId,
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );
}
