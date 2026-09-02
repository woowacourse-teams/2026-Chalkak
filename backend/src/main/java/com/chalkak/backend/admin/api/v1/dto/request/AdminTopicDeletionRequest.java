package com.chalkak.backend.admin.api.v1.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 주제 삭제 요청")
public record AdminTopicDeletionRequest(
        @NotBlank
        @Size(max = 500)
        @Schema(description = "삭제 사유", example = "주제 편성 변경")
        String reason
) {
}
