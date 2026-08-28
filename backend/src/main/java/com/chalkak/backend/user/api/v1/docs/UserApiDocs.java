package com.chalkak.backend.user.api.v1.docs;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.exception.ErrorResponse;
import com.chalkak.backend.user.api.v1.dto.request.UserSignatureUpdateRequest;
import com.chalkak.backend.user.api.v1.dto.response.UserSignatureDetailResponse;
import com.chalkak.backend.user.api.v1.dto.response.UserSignatureResponse;
import com.chalkak.backend.user.api.v1.dto.response.UserSignatureUploadResponse;
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

@Tag(name = "Users", description = "사용자 API")
@SecurityRequirement(name = "accessToken")
public interface UserApiDocs {

    @Operation(summary = "회원 탈퇴")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "회원 탈퇴 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 인증 정보",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "회원을 찾을 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<Void> withdraw(
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );

    @Operation(summary = "사인 이미지 업로드 URL 발급")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "업로드 URL 발급 성공",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 인증 정보",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "회원을 찾을 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<UserSignatureUploadResponse> createSignatureUpload(
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );

    @Operation(summary = "내 사인 조회")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "사인 조회 성공",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "사인 재등록 필요(SIGNATURE_REGISTRATION_REQUIRED): "
                            + "사인 이미지 처리 실패 또는 설정된 처리 제한 시간 초과",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 인증 정보",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "회원을 찾을 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<UserSignatureDetailResponse> getSignature(
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );

    @Operation(summary = "사인 이미지 수정")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "사인 이미지 수정 성공",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 이미지 업로드 정보 또는 이미지 재업로드 필요"
                            + "(SIGNATURE_REUPLOAD_REQUIRED)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 인증 정보",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "회원 또는 업로드 이미지를 찾을 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<UserSignatureResponse> updateSignature(
            @Parameter(hidden = true) AuthenticatedUser loginUser,
            UserSignatureUpdateRequest request
    );
}
