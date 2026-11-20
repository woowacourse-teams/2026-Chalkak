package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.post.domain.ModerationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminPostCorrectionRequest(
        @NotNull
        ModerationStatus status,
        @NotBlank
        @Size(max = 500)
        String reason
) {
}
