package com.chalkak.backend.post.api.internal.v1.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record PostImageProcessingFailRequest(
        @Schema(
                description = "거절 사유 코드",
                example = "UNSUPPORTED_FORMAT",
                allowableValues = {
                        "UNSUPPORTED_FORMAT",
                        "ANIMATED_IMAGE",
                        "TOO_LARGE",
                        "TOO_MANY_PIXELS",
                        "MISSING_OBJECT"
                }
        )
        String reason
) {
}
