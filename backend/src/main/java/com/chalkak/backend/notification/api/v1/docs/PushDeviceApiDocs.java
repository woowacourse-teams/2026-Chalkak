package com.chalkak.backend.notification.api.v1.docs;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.exception.ErrorResponse;
import com.chalkak.backend.notification.api.v1.dto.request.PushDeviceRegistrationRequest;
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

@Tag(name = "PushDevices", description = "현재 로그인 기기의 푸시 등록 API")
@SecurityRequirement(name = "accessToken")
public interface PushDeviceApiDocs {

    @Operation(summary = "현재 로그인 기기 등록·갱신", description = "회원과 로그인 ID는 액세스 토큰에서 식별합니다. 같은 로그인에서 반복 등록해도 최초 등록 시각을 유지합니다. 다른 로그인이 같은 FCM 토큰을 사용 중이면 이전 연결을 비활성화합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "등록·갱신 성공"),
            @ApiResponse(responseCode = "400", description = "FCM 토큰 누락 또는 잘못된 요청 본문", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: 유효하지 않은 인증 또는 탈퇴 회원. REAUTHENTICATION_REQUIRED: 로그인 ID가 없거나 유효한 로그인이 아니므로 다시 로그인 필요", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "회원 액세스 토큰이 아닌 인증 정보", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> registerCurrentDevice(
            PushDeviceRegistrationRequest request,
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );
}
