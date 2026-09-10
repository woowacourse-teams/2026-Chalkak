package com.chalkak.backend.post.api.v1.dto.response;

import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.service.TodayPostStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

public record TodayPostStatusResponse(
        @Schema(
                description = "참여할 수 있는 주제의 날짜. 참여 기간이 자정을 넘기면 어제 날짜일 수 있다",
                example = "2026-09-09"
        )
        LocalDate topicDate,

        @Schema(
                description = "이 주제에 새 게시물을 작성할 수 없으면 true",
                example = "true"
        )
        boolean isPosted,

        @Schema(
                description = "이 주제에 작성한 게시물 ID. isPosted가 false이면 null",
                example = "0198f6c1-62ba-7d30-8b12-0f733b6570d4",
                nullable = true
        )
        UUID postId,

        @Schema(
                description = "이 주제에 작성한 게시물의 검수 상태. isPosted가 false이면 null",
                allowableValues = {"VALIDATING", "PENDING", "APPROVED"},
                nullable = true
        )
        ModerationStatus moderationStatus
) {

    public static TodayPostStatusResponse from(TodayPostStatus status) {
        return new TodayPostStatusResponse(
                status.topicDate(),
                status.isPosted(),
                status.postId(),
                status.moderationStatus()
        );
    }
}
