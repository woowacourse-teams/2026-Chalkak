package com.chalkak.backend.auth.api.v1.docs;

import com.chalkak.backend.auth.api.v1.dto.request.SocialLoginRequest;
import com.chalkak.backend.auth.api.v1.dto.response.SocialLoginResponse;
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
    ResponseEntity<SocialLoginResponse> socialLogin(SocialLoginRequest request);
}
