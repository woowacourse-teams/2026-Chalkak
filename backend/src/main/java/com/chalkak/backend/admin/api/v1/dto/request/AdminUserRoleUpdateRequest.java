package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.user.domain.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminUserRoleUpdateRequest(
        @NotNull
        @Schema(allowableValues = "ARTIST")
        UserRole role,
        @NotBlank
        @Schema(description = "작가 권한 부여 사유. 앞뒤 공백을 제거한 후 최대 500자")
        String reason
) {
}
