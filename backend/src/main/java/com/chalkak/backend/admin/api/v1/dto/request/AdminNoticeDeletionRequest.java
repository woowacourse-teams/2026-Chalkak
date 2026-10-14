package com.chalkak.backend.admin.api.v1.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 공지 삭제 요청")
public record AdminNoticeDeletionRequest(
        @NotBlank
        @Size(max = 500)
        @Schema(description = "삭제 사유", example = "점검 일정 취소")
        String reason
) {
}
