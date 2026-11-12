package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.AdminUserListRequest;
import com.chalkak.backend.admin.api.v1.dto.request.AdminUserRoleUpdateRequest;
import com.chalkak.backend.admin.api.v1.dto.request.AdminUserStatusUpdateRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminUserDetailResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminUserListResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminUserRoleResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminUserStatusResponse;
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

    @Operation(summary = "관리자 사용자 목록 조회", description = "활성 사용자를 기본으로 차단·탈퇴 상태와 이메일을 선택해 검색합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "관리자 사용자 목록 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 필터 또는 페이지 조건", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminUserListResponse> getUsers(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @ParameterObject AdminUserListRequest request);

    @Operation(summary = "관리자 사용자 상세 조회", description = "탈퇴 여부와 관계없이 사용자 상태·소셜 제공자와 "
            + "승인 대기·승인·거절 게시물 개수를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "관리자 사용자 상세 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 사용자 ID", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminUserDetailResponse> getUser(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @Parameter(description = "사용자 ID", schema = @Schema(type = "string", format = "uuid")) String userId);

    @Operation(summary = "관리자 사용자 상태 변경", description = "필수 사유를 남겨 사용자를 차단하거나 차단 해제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "사용자 상태 변경 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 상태·사유 또는 이미 변경된 상태", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminUserStatusResponse> updateStatus(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @Parameter(description = "사용자 ID", schema = @Schema(type = "string", format = "uuid")) String userId,
            AdminUserStatusUpdateRequest request
    );

    @Operation(summary = "관리자 작가 권한 부여", description = "일반 회원에게 사유와 함께 ARTIST 역할을 부여합니다. 원작 확약 동의는 회원이 직접 기록합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "작가 권한 부여 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 요청, 이미 작가인 회원 또는 탈퇴 회원", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 API 접근 불가", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminUserRoleResponse> updateRole(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin,
            @Parameter(description = "사용자 ID", schema = @Schema(type = "string", format = "uuid")) String userId,
            AdminUserRoleUpdateRequest request
    );
}
