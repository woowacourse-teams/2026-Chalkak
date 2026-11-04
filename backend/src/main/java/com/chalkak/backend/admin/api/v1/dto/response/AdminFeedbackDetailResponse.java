package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.api.v1.dto.response.AdminFeedbackListResponse.AuthorResponse;
import com.chalkak.backend.admin.service.feedback.AdminFeedbackDetailResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record AdminFeedbackDetailResponse(
        UUID feedbackId,
        String content,
        Instant createdAt,
        @Schema(description = "문의 삭제 시각. 삭제되지 않았으면 null", nullable = true)
        Instant deletedAt,
        AuthorResponse author
) {

    public static AdminFeedbackDetailResponse from(AdminFeedbackDetailResult result) {
        return new AdminFeedbackDetailResponse(
                result.feedbackId(),
                result.content(),
                result.createdAt(),
                result.deletedAt(),
                AuthorResponse.from(result.author())
        );
    }
}
