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
        @Schema(description = "관리자 답변 내용. 미답변이면 null", nullable = true)
        String answerContent,
        @Schema(description = "답변 등록 시각. 미답변이면 null", nullable = true)
        Instant answeredAt,
        @Schema(description = "문의 삭제 시각. 삭제되지 않았으면 null", nullable = true)
        Instant deletedAt,
        AuthorResponse author
) {

    public static AdminFeedbackDetailResponse from(AdminFeedbackDetailResult result) {
        return new AdminFeedbackDetailResponse(
                result.feedbackId(),
                result.content(),
                result.createdAt(),
                result.answerContent(),
                result.answeredAt(),
                result.deletedAt(),
                AuthorResponse.from(result.author())
        );
    }
}
