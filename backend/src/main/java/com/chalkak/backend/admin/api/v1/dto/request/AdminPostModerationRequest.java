package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.post.domain.ModerationStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record AdminPostModerationRequest(
        @NotNull(message = "검수 상태가 필요합니다.")
        @Schema(
                description = "확정할 검수 상태",
                allowableValues = {"APPROVED", "REJECTED"},
                example = "REJECTED"
        )
        ModerationStatus status,
        @Schema(
                description = "거절 사유. REJECTED일 때 필수이며 최대 500자입니다.",
                example = "운영 정책 위반",
                nullable = true,
                maxLength = 500
        )
        String rejectionReason
) {

    private static final int MAX_REJECTION_REASON_LENGTH = 500;

    @JsonIgnore
    @AssertTrue(message = "승인 요청에는 거절 사유를 입력할 수 없고, 거절 요청에는 500자 이하의 사유가 필요합니다.")
    public boolean isValidDecision() {
        if (status == null) {
            return true;
        }
        if (status == ModerationStatus.APPROVED) {
            return rejectionReason == null;
        }
        if (status != ModerationStatus.REJECTED) {
            return false;
        }
        if (rejectionReason == null || rejectionReason.isBlank()) {
            return false;
        }
        String normalizedReason = rejectionReason.trim();
        return normalizedReason.codePointCount(0, normalizedReason.length())
                <= MAX_REJECTION_REASON_LENGTH;
    }
}
