package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.AdminUserStatusResult;
import com.chalkak.backend.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "관리자 사용자 상태 변경 응답")
public record AdminUserStatusResponse(
        @Schema(description = "사용자 ID", format = "uuid")
        UUID userId,
        @Schema(description = "변경된 사용자 상태")
        UserStatus status
) {

    public static AdminUserStatusResponse from(AdminUserStatusResult result) {
        return new AdminUserStatusResponse(result.userId(), result.status());
    }
}
