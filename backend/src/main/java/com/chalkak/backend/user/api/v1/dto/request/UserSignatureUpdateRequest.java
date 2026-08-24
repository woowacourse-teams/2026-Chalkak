package com.chalkak.backend.user.api.v1.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UserSignatureUpdateRequest(
        @NotNull(message = "사인 이미지 업로드 정보가 올바르지 않습니다.")
        UUID signatureOriginalUploadId
) {
}
