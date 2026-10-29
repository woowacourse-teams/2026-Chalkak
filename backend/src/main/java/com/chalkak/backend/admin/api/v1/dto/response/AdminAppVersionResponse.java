package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.appversion.AdminAppVersionService.SettingResult;
import com.chalkak.backend.appversion.domain.AppPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record AdminAppVersionResponse(
        AppPlatform platform,
        @Schema(
                types = {
                        "string", "null"})
        String minimumSupportedVersion,
        @Schema(types = {"string", "null"})
        String storeUrl,
        @Schema(types = {"integer", "null"})
        Long revision,
        @Schema(types = {"string", "null"})
        UUID lastChangedBy,
        @Schema(types = {"string", "null"})
        String changeReason,
        @Schema(types = {"string", "null"})
        Instant createdAt,
        @Schema(types = {"string", "null"})
        Instant updatedAt
) {

    public static AdminAppVersionResponse from(SettingResult result) {
        return new AdminAppVersionResponse(
                result.platform(),
                result.minimumSupportedVersion(),
                result.storeUrl(),
                result.revision(),
                result.lastChangedBy(),
                result.changeReason(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
