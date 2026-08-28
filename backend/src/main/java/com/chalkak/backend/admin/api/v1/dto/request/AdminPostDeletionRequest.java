package com.chalkak.backend.admin.api.v1.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record AdminPostDeletionRequest(
        @NotBlank(message = "삭제 사유가 필요합니다.")
        @Schema(
                description = "관리자 게시물 삭제 사유. 입력 원문은 최대 500자이며 앞뒤 공백을 제거해 저장합니다.",
                example = "운영 정책 위반",
                minLength = 1,
                maxLength = 500,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String reason
) {

    private static final int MAX_REASON_LENGTH = 500;

    @JsonIgnore
    @AssertTrue(message = "삭제 사유는 500자 이하여야 합니다.")
    public boolean isValidReasonLength() {
        if (reason == null || reason.isBlank()) {
            return true;
        }
        return reason.codePointCount(0, reason.length())
                <= MAX_REASON_LENGTH;
    }
}
