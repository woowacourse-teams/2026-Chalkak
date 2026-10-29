package com.chalkak.backend.appversion.api.v1.dto.response;

import com.chalkak.backend.appversion.domain.AppPlatform;
import com.chalkak.backend.appversion.service.AppVersionResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record AppVersionResponse(
        AppPlatform platform,
        @Schema(
                description = "미설정이면 null이며 업데이트 제한을 적용하지 않습니다.",
                types = {
                        "string",
                        "null"
                })
        String minimumSupportedVersion,
        @Schema(
                description = "미설정이면 null입니다.",
                types = {
                        "string",
                        "null"
                })
        String storeUrl
) {

    public static AppVersionResponse from(AppVersionResult result) {
        return new AppVersionResponse(
                result.platform(),
                result.minimumSupportedVersion(),
                result.storeUrl()
        );
    }
}
