package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.api.v1.dto.response.AdminAiReviewListResponse.AuthorResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminAiReviewListResponse.PromptVersionResponse;
import com.chalkak.backend.admin.api.v1.dto.response.AdminAiReviewListResponse.UsageResponse;
import com.chalkak.backend.admin.service.aireview.AdminAiReviewQueryService.AiReviewDetail;
import com.chalkak.backend.admin.service.aireview.AdminAiReviewQueryService.AiReviewSummary;
import com.chalkak.backend.aireview.domain.AiReview;
import com.chalkak.backend.aireview.domain.AiReviewResult;
import com.chalkak.backend.post.domain.ModerationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record AdminAiReviewDetailResponse(
        UUID aiReviewId,
        AiReview.Status status,
        @Schema(description = "마지막 실패 오류 코드. 재시도 대기 중인 대기 상태에도 남습니다.", nullable = true)
        String errorCode,
        @Schema(description = "사용자 요청 횟수(처음 요청 포함)")
        int requestCount,
        @Schema(description = "종합 점수. 완료된 리뷰만 값이 있고 그 밖에는 null입니다.", nullable = true)
        Double overallScore,
        PromptVersionResponse promptVersion,
        PostResponse post,
        AuthorResponse author,
        @Schema(description = "마지막 호출의 토큰 사용량. 호출 기록이 없으면 각 값이 null입니다.")
        UsageResponse usage,
        @Schema(description = "마지막 호출 지연시간(ms)", nullable = true)
        Long latencyMs,
        @Schema(description = "처음 요청 시각")
        Instant requestedAt,
        @Schema(description = "이번 회차의 요청 시각")
        Instant lastRequestedAt,
        @Schema(description = "워커가 처음 가져간 시각. 아직 가져가지 않았으면 null입니다.", nullable = true)
        Instant startedAt,
        @Schema(nullable = true)
        Instant completedAt,
        @Schema(
                description = "작성자 앱에서 이 리뷰가 보이는지. 게시물이 승인됐고 게시물·주제·사진·작성자가 모두 삭제되지 않았을 때만 true입니다. 정지된 작성자는 영향을 주지 않습니다.")
        boolean visibleToAuthor,
        @Schema(description = "워커가 가져간 누적 횟수. 재시도와 임대 회수를 모두 세며 줄지 않습니다.")
        int attempt,
        @Schema(description = "마지막 OpenAI 응답 ID. 응답을 받지 못했으면 null입니다.", nullable = true)
        String openaiResponseId,
        @Schema(description = "서비스 규칙으로 변환한 리뷰 결과. 완료된 리뷰만 값이 있습니다.", nullable = true)
        AiReviewResult result,
        @Schema(
                types = {
                        "object"},
                description = "모델이 돌려준 원출력(구도 점수, 주제 표현 점수, 피드백 등)을 JSON 객체로 그대로 담습니다. 없으면 null입니다.")
        JsonNode output
) {

    public static AdminAiReviewDetailResponse from(AiReviewDetail detail) {
        AiReviewSummary summary = detail.summary();
        return new AdminAiReviewDetailResponse(
                summary.aiReviewId(),
                summary.status(),
                summary.errorCode(),
                summary.requestCount(),
                summary.overallScore(),
                PromptVersionResponse.from(summary.promptVersion()),
                new PostResponse(
                        summary.post().postId(),
                        summary.post().thumbnailUrl(),
                        detail.originalImageUrl(),
                        summary.post().moderationStatus(),
                        summary.post().deletedAt()
                ),
                AuthorResponse.from(summary.author()),
                UsageResponse.from(summary.usage()),
                summary.latencyMs(),
                summary.requestedAt(),
                summary.lastRequestedAt(),
                detail.startedAt(),
                summary.completedAt(),
                summary.visibleToAuthor(),
                detail.attempt(),
                detail.openaiResponseId(),
                detail.result(),
                detail.output()
        );
    }

    @Schema(name = "AdminAiReviewDetailPost")
    public record PostResponse(
            UUID postId,
            @Schema(nullable = true)
            String thumbnailUrl,
            @Schema(description = "원본 사진 URL", nullable = true)
            String imageUrl,
            ModerationStatus moderationStatus,
            @Schema(nullable = true)
            Instant deletedAt
    ) {
    }
}
