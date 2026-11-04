package com.chalkak.backend.admin.api.v1.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AdminFeedbackAnswerRequest(
        @NotBlank(message = "답변 내용이 필요합니다.")
        @Schema(description = "앞뒤 공백을 제거한 뒤 Unicode 코드 포인트 기준 1~3,000자")
        String answerContent
) {
}
