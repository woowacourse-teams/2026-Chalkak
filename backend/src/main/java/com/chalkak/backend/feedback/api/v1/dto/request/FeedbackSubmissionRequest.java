package com.chalkak.backend.feedback.api.v1.dto.request;

import com.chalkak.backend.feedback.domain.Feedback;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record FeedbackSubmissionRequest(
        @NotBlank(message = "피드백 내용이 필요합니다.")
        @Schema(
                description = "피드백 내용. 앞뒤 공백을 제거한 후 최대 1000자이며 앞뒤 공백을 제거해 저장합니다.",
                example = "사진 업로드 후 화면이 멈춰요.",
                minLength = 1,
                maxLength = Feedback.MAX_CONTENT_LENGTH,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String content
) {

    @JsonIgnore
    @AssertTrue(message = "피드백 내용은 1000자 이하여야 합니다.")
    public boolean isValidContentLength() {
        if (content == null || content.isBlank()) {
            return true;
        }
        return Feedback.isWithinMaxLength(content);
    }
}
