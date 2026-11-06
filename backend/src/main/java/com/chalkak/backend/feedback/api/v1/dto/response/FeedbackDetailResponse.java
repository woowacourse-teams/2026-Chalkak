package com.chalkak.backend.feedback.api.v1.dto.response;

import com.chalkak.backend.feedback.service.FeedbackDetailResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record FeedbackDetailResponse(
        UUID feedbackId,
        String content,
        Instant createdAt,
        @Schema(description = "관리자 답변 내용. 미답변이면 null", nullable = true)
        String answerContent,
        @Schema(description = "답변 등록 시각. 미답변이면 null", nullable = true)
        Instant answeredAt
) {

    public static FeedbackDetailResponse from(FeedbackDetailResult result) {
        return new FeedbackDetailResponse(
                result.feedbackId(),
                result.content(),
                result.createdAt(),
                result.answerContent(),
                result.answeredAt()
        );
    }
}
