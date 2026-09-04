package com.chalkak.backend.auth.api.v1.docs;

import com.chalkak.backend.auth.api.v1.dto.request.RefreshTokenRequest;
import com.chalkak.backend.auth.api.v1.dto.request.SocialIdTokenRequest;
import com.chalkak.backend.auth.api.v1.dto.request.SocialSignupRequest;
import com.chalkak.backend.auth.api.v1.dto.response.SocialLoginResponse;
import com.chalkak.backend.auth.api.v1.dto.response.SocialSignupResponse;
import com.chalkak.backend.auth.api.v1.dto.response.SocialSignupSignatureUploadResponse;
import com.chalkak.backend.auth.api.v1.dto.response.TokenRefreshResponse;
import com.chalkak.backend.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Auth", description = "인증 API")
public interface AuthApiDocs {

    @Operation(summary = "소셜 로그인")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공(차단 회원 포함) 또는 회원가입 필요",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 또는 지원하지 않는 제공자",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 ID Token",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "탈퇴한 차단 소셜 계정",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<SocialLoginResponse> socialLogin(SocialIdTokenRequest request);

    @Operation(summary = "소셜 회원가입용 서명 이미지 업로드 URL 발급")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "업로드 URL 발급 성공",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 또는 이미 가입된 소셜 계정",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 ID Token",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "차단된 소셜 계정",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<SocialSignupSignatureUploadResponse>
            createSocialSignupSignatureUpload(
                    SocialIdTokenRequest request
            );

    @Operation(summary = "소셜 회원가입 완료")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "회원가입 완료 또는 기존 회원가입 결과 반환",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청, 처리 중인 서명 이미지 또는 사용할 수 없는 이미지",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않거나 만료된 회원가입 토큰",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "차단된 소셜 계정",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "업로드한 서명 이미지를 찾을 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<SocialSignupResponse> socialSignup(SocialSignupRequest request);

    @Operation(
            summary = "액세스 토큰 재발급",
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
                            + " 저장한 토큰을 버리고 로그인 화면으로 보내야 한다",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<TokenRefreshResponse> refresh(RefreshTokenRequest request);

    @Operation(
            summary = "로그아웃",
            description = "리프레시 토큰이 가리키는 기기 세션 하나만 끊는다."
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
    ResponseEntity<Void> logout(RefreshTokenRequest request);
}
