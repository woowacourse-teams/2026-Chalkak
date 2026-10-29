package com.chalkak.backend.admin.api.v1.docs;

import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.AdminAppVersionRequest;
import com.chalkak.backend.admin.api.v1.dto.response.AdminAppVersionListResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminAppVersionResponse;
import com.chalkak.backend.appversion.domain.AppPlatform;
import com.chalkak.backend.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin App Versions", description = "플랫폼별 앱 최소 지원 버전 관리")
public interface AdminAppVersionApiDocs {

    @Operation(summary = "앱 버전 설정 목록 조회", description = "ANDROID·IOS 순서로 두 플랫폼을 반환합니다. 미설정이면 platform 외 필드는 null입니다. 이전에 수동 입력한 설정은 최종 관리자·사유가 null일 수 있습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "플랫폼별 설정 조회", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "403", description = "관리자 권한 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminAppVersionListResponse> getAppVersions(
            @Parameter(hidden = true) AuthenticatedAdmin admin);

    @Operation(summary = "앱 버전 설정 저장", description = "최초 입력과 수정 모두 200입니다. 조회한 revision을 전달하며 최초 입력은 null입니다. 같은 버전·주소면 이전 revision의 재시도도 이력 없이 현재 값을 반환합니다. 그 외 오래된 요청은 RESOURCE_STATE_CHANGED로 거절합니다. 버전 하향을 허용합니다. 실제 앱 식별과 출시 여부는 관리자가 확인해야 합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "현재 설정", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 입력·플랫폼 또는 수정 충돌(RESOURCE_STATE_CHANGED)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminAppVersionResponse> saveAppVersion(
            @Parameter(hidden = true) AuthenticatedAdmin admin,
            AppPlatform platform,
            AdminAppVersionRequest request
    );
}
