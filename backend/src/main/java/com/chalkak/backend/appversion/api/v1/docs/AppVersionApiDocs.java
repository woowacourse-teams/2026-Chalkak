package com.chalkak.backend.appversion.api.v1.docs;

import com.chalkak.backend.appversion.api.v1.dto.response.AppVersionResponse;
import com.chalkak.backend.appversion.domain.AppPlatform;
import com.chalkak.backend.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "App Versions", description = "앱 최소 지원 버전 조회")
public interface AppVersionApiDocs {

    @Operation(summary = "앱 최소 지원 버전 조회", description = "토큰 없이 조회할 수 있습니다. 관리자가 아직 설정하지 않은 플랫폼은 최소 지원 버전과 스토어 주소가 모두 null이며 업데이트 제한을 적용하지 않습니다. 응답은 캐시하지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "설정 또는 미설정 상태 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "platform 누락 또는 잘못된 플랫폼", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "제공한 토큰이 유효하지 않음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "회원·관리자 scope가 아닌 토큰 접근 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AppVersionResponse> getAppVersion(AppPlatform platform);
}
