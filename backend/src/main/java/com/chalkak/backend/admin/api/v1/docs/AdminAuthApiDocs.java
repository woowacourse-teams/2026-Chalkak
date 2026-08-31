package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.AdminLoginRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminLoginResponse;
import com.chalkak.backend.admin.api.v1.dto.response.CurrentAdminResponse;
import com.chalkak.backend.exception.ErrorResponse;
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

@Tag(name = "Admin Auth", description = "관리자 인증 API")
public interface AdminAuthApiDocs {

    @Operation(summary = "관리자 로그인")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공", useReturnTypeSchema = true),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "아이디 또는 비밀번호 불일치",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<AdminLoginResponse> login(AdminLoginRequest request);

    @Operation(
            summary = "현재 관리자 조회",
            security = @SecurityRequirement(name = "adminAccessToken")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "현재 관리자 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "401", description = "관리자 인증 필요",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CurrentAdminResponse> getCurrentAdmin(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin
    );

    @Operation(
            summary = "관리자 로그아웃",
            description = "응답 후 클라이언트가 토큰을 폐기합니다. 서버에서 이미 발급한 JWT를 즉시 무효화하지는 않습니다.",
            security = @SecurityRequirement(name = "adminAccessToken")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "로그아웃 처리 성공"),
            @ApiResponse(responseCode = "401", description = "관리자 인증 필요",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> logout(
            @Parameter(hidden = true) AuthenticatedAdmin authenticatedAdmin
    );
}
