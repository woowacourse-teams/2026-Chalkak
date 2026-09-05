package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.AdminLoginRequest;
import com.chalkak.backend.admin.api.v1.dto.request.AdminRefreshTokenRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminLoginResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminTokenRefreshResponse;
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
            summary = "관리자 액세스 토큰 재발급",
            description = "리프레시 토큰 자체가 자격증명이므로 액세스 토큰 없이 호출한다."
                    + " 성공하면 리프레시 토큰도 함께 회전하므로 요청에 사용한 토큰은 버리고"
                    + " 응답의 리프레시 토큰으로 교체해야 한다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "재발급 성공",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 본문이 없거나 리프레시 토큰이 비어 있음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "재로그인 필요."
                            + " 알 수 없거나 만료됐거나 이미 사용한 리프레시 토큰이며,"
                            + " errorCode는 REAUTHENTICATION_REQUIRED다."
                            + " 저장한 토큰을 버리고 관리자 로그인 화면으로 보내야 한다",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<AdminTokenRefreshResponse> refresh(AdminRefreshTokenRequest request);

    @Operation(
            summary = "관리자 로그아웃",
            description = "리프레시 토큰이 가리키는 기기 세션 하나만 끊는다."
                    + " 리프레시 토큰 자체가 자격증명이므로 액세스 토큰 없이 호출한다."
                    + " 이미 발급된 액세스 토큰은 즉시 무효화되지 않고 최대 15분 동안 유효하지만,"
                    + " 그 뒤로는 재발급이 막혀 세션이 끊긴다."
                    + " 알 수 없거나 이미 폐기된 토큰에도 204를 반환한다."
                    + " 실패를 알리면 토큰의 존재 여부가 새어 나가고,"
                    + " 재시도한 클라이언트가 로그아웃하지 못하고 막히기 때문이다"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "로그아웃 처리 완료"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 본문이 없거나 리프레시 토큰이 비어 있음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<Void> logout(AdminRefreshTokenRequest request);
}
