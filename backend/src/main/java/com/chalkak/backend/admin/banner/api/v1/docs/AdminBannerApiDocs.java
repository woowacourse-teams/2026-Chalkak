package com.chalkak.backend.admin.banner.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.banner.api.v1.dto.request.AdminBannerListRequest;
import com.chalkak.backend.admin.banner.api.v1.dto.request.AdminBannerRequest;
import com.chalkak.backend.admin.banner.api.v1.dto.response.AdminBannerListResponse;
import com.chalkak.backend.admin.banner.api.v1.dto.response.AdminBannerResponse;
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

@Tag(name = "Admin Banners", description = "관리자 배너 관리")
public interface AdminBannerApiDocs {

    @Operation(summary = "배너 목록 조회", description = "등록 시각 내림차순, 같은 시각이면 ID 내림차순으로 조회합니다. 배너가 없거나 페이지 범위를 벗어나면 빈 목록을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 페이지 번호·크기 또는 조회 offset 범위 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminBannerListResponse> getBanners(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @ParameterObject AdminBannerListRequest request);

    @Operation(summary = "배너 등록", description = "외부 페이지의 URL과 배너 정보를 등록합니다. 웹뷰 URL의 앞뒤 공백을 제거한 전체 문자열이 기존 배너와 같으면 거절합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "필수 값 누락·공백, 길이 초과, 잘못된 HTTPS 주소 또는 중복 웹뷰 URL", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminBannerResponse> createBanner(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            AdminBannerRequest request);
}
