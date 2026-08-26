package com.chalkak.backend.auth.api.v1.docs;

import com.chalkak.backend.auth.api.v1.dto.request.SocialIdTokenRequest;
import com.chalkak.backend.auth.api.v1.dto.request.SocialSignupRequest;
import com.chalkak.backend.auth.api.v1.dto.response.SocialLoginResponse;
import com.chalkak.backend.auth.api.v1.dto.response.SocialSignupResponse;
import com.chalkak.backend.auth.api.v1.dto.response.SocialSignupSignatureUploadResponse;
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
                    description = "로그인 성공 또는 회원가입 필요",
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
                    description = "유효하지 않은 ID Token 또는 탈퇴 회원",
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
                    description = "유효하지 않거나 만료된 회원가입 토큰 또는 탈퇴 회원",
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
}
