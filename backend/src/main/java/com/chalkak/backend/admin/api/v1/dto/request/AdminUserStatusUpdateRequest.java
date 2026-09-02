package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 사용자 상태 변경 요청")
public record AdminUserStatusUpdateRequest(
        @NotNull
        @Schema(description = "변경할 사용자 상태", example = "BANNED")
        UserStatus status,
        @NotBlank
        @Size(max = 500)
        @Schema(description = "차단 또는 해제 사유", example = "반복적인 운영 정책 위반")
        String reason
) {
}
