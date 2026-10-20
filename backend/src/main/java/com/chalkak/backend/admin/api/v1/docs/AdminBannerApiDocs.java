package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.AdminBannerRequest;
import com.chalkak.backend.banner.api.v1.dto.response.BannerResponse;
import com.chalkak.backend.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Banners", description = "관리자 배너 관리")
public interface AdminBannerApiDocs {

    @Operation(summary = "배너 등록", description = "외부 페이지의 URL과 배너 정보를 등록합니다. 웹뷰 URL의 앞뒤 공백을 제거한 전체 문자열이 기존 배너와 같으면 거절합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "필수 값 누락·공백, 길이 초과, 잘못된 HTTPS 주소·포트 범위 초과 또는 중복 웹뷰 URL", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<BannerResponse> createBanner(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            AdminBannerRequest request);

    @Operation(summary = "배너 삭제", description = "배너를 논리 삭제합니다. 요청 본문 없이 호출하며 재삭제하면 최초 삭제 결과를 유지하고 감사 기록을 추가하지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공 또는 이미 삭제된 배너"),
            @ApiResponse(responseCode = "400", description = "잘못된 ID", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 배너", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> deleteBanner(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @Parameter(description = "배너 ID", schema = @Schema(type = "string", format = "uuid")) String bannerId);
}
