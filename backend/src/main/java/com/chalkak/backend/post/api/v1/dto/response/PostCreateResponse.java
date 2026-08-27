package com.chalkak.backend.post.api.v1.dto.response;

import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.service.PostCreationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record PostCreateResponse(
        @Schema(
                description = "생성된 게시물 ID",
                example = "0198f6c1-62ba-7d30-8b12-0f733b6570d5"
        )
        UUID postId,

        @Schema(
                description = "이미지 처리 전 VALIDATING, 처리 완료 후 관리자 검수 대기이면 PENDING",
                allowableValues = {"VALIDATING", "PENDING"},
                example = "VALIDATING"
        )
        ModerationStatus moderationStatus
) {

    public static PostCreateResponse from(PostCreationResult result) {
        return new PostCreateResponse(result.postId(), result.moderationStatus());
    }
}
