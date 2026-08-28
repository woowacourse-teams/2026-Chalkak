package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.AdminUserListRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminUserDetailResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminUserListResponse;
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

@Tag(name = "Admin Users", description = "관리자 사용자 조회 API")
public interface AdminUserApiDocs {

    @Operation(
            summary = "관리자 사용자 목록 조회",
            description = "탈퇴자를 포함한 사용자를 파생 상태와 이메일로 검색합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "관리자 사용자 목록 조회 성공",
                    useReturnTypeSchema = true),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 필터 또는 페이지 조건",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 API 접근 불가",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminUserListResponse> getUsers(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @ParameterObject AdminUserListRequest request);

    @Operation(
            summary = "관리자 사용자 상세 조회",
            description = "탈퇴 여부와 관계없이 사용자 상태·소셜 제공자·게시물 개수를 조회합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "관리자 사용자 상세 조회 성공",
                    useReturnTypeSchema = true),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 사용자 ID",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 API 접근 불가",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminUserDetailResponse> getUser(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @Parameter(
                    description = "사용자 ID",
                    schema = @Schema(type = "string", format = "uuid"))
            String userId);
}
