package com.chalkak.backend.post.api.v1.dto.response;

import com.chalkak.backend.post.domain.PostRegistrationLimit;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record PostRegistrationLimitResponse(
        @Schema(description = "KST 하루 등록 상한. 최초 등록 포함", example = "10")
        int limit,

        @Schema(description = "오늘 성공한 등록 수. 삭제·반려·이미지 처리 실패 포함", example = "3")
        long usedCount,

        @Schema(description = "남은 등록 수. 기존 한 건 정책·참여 기간의 허용 여부와는 별도", example = "7")
        int remainingCount,

        @Schema(description = "다음 KST 자정의 UTC 시각", example = "2026-11-19T15:00:00Z")
        Instant resetsAt
) {

    public static PostRegistrationLimitResponse from(PostRegistrationLimit registrationLimit) {
        return new PostRegistrationLimitResponse(
                registrationLimit.limit(),
                registrationLimit.usedCount(),
                registrationLimit.remainingCount(),
                registrationLimit.resetsAt()
        );
    }
}
