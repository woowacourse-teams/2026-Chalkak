package com.chalkak.backend.user.api.v1.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UserSignatureUpdateRequest(
        @Schema(
                description = "사인 원본 이미지 업로드 ID",
                example = "0198f6c1-62ba-7d30-8b12-0f733b6570d4"
        )
        @NotNull(message = "사인 이미지 업로드 정보가 올바르지 않습니다.")
        UUID signatureOriginalUploadId
) {
}
