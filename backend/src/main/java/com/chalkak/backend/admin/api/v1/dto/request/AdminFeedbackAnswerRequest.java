package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.feedback.domain.Feedback;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record AdminFeedbackAnswerRequest(
        @NotBlank(message = "답변 내용이 필요합니다.")
        @Schema(
                description = "앞뒤 공백을 제거한 뒤 Unicode 코드 포인트 기준 1~3,000자",
                minLength = 1,
                maxLength = Feedback.MAX_ANSWER_LENGTH,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String answerContent
) {

    @JsonIgnore
    @AssertTrue(message = "답변은 1~3,000자로 입력해 주세요.")
    public boolean isValidAnswerContentLength() {
        if (answerContent == null || answerContent.isBlank()) {
            return true;
        }
        return Feedback.isWithinMaxAnswerLength(answerContent);
    }
}
