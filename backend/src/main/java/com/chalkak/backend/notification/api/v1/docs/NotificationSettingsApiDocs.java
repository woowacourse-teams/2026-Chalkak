package com.chalkak.backend.notification.api.v1.docs;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.exception.ErrorResponse;
import com.chalkak.backend.notification.api.v1.dto.response.NotificationSettingsResponse;
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

@Tag(name = "NotificationSettings", description = "내 푸시 수신 설정 API")
@SecurityRequirement(name = "accessToken")
public interface NotificationSettingsApiDocs {

    @Operation(summary = "내 푸시 수신 설정 조회", description = "회원 계정에 저장된 주제·승인·반려 푸시 수신 설정을 조회합니다. 최초 설정은 두 항목 모두 true이며 회원의 모든 기기에 공통으로 적용됩니다. 운영체제의 알림 권한을 나타내지는 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: 유효하지 않은 인증 정보 또는 탈퇴 회원", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "회원 액세스 토큰이 아닌 인증 정보", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<NotificationSettingsResponse> getSettings(
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );
}
