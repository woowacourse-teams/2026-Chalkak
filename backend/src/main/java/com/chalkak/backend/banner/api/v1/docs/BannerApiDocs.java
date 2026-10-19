package com.chalkak.backend.banner.api.v1.docs;

import com.chalkak.backend.banner.api.v1.dto.request.BannerListRequest;
import com.chalkak.backend.banner.api.v1.dto.response.BannerListResponse;
import com.chalkak.backend.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

@Tag(name = "Banners", description = "공통 배너 조회")
public interface BannerApiDocs {

    @Operation(summary = "배너 목록 조회", description = "토큰 없이 또는 회원·관리자 토큰으로 같은 미삭제 배너 목록을 조회합니다. 등록 시각 내림차순, 같은 시각이면 ID 내림차순입니다. 배너가 없거나 페이지 범위를 벗어나면 빈 목록을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 페이지 번호·크기 또는 조회 offset 범위 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "제공한 토큰이 유효하지 않음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "회원·관리자 scope가 아닌 토큰 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<BannerListResponse> getBanners(
            @ParameterObject BannerListRequest request);
}
