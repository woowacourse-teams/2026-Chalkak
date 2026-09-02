package com.chalkak.backend.post.api.internal.v1.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostImageProcessingFailRequest(
        @NotBlank
        @Size(max = MAX_REASON_LENGTH)
        @Schema(
                description = "거절 사유 코드",
                example = "UNSUPPORTED_FORMAT",
                allowableValues = {
                        "UNSUPPORTED_FORMAT",
                        "CORRUPTED_IMAGE",
                        "ANIMATED_IMAGE",
                        "TOO_LARGE",
                        "TOO_MANY_PIXELS",
                        "MISSING_OBJECT",
                        "PROCESSING_ERROR"
                }
        )
        String reason
) {

    public static final int MAX_REASON_LENGTH = 50;
}
