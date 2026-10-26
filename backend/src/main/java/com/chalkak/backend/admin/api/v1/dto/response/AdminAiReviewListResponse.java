package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.aireview.AdminAiReviewQueryService.AiReviewSummary;
import com.chalkak.backend.admin.service.aireview.AdminAiReviewQueryService.AuthorSummary;
import com.chalkak.backend.admin.service.aireview.AdminAiReviewQueryService.ListResult;
import com.chalkak.backend.admin.service.aireview.AdminAiReviewQueryService.PostSummary;
import com.chalkak.backend.admin.service.aireview.AdminAiReviewQueryService.PromptVersionSummary;
import com.chalkak.backend.admin.service.aireview.AdminAiReviewQueryService.UsageSummary;
import com.chalkak.backend.aireview.domain.AiReview;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminAiReviewListResponse(
        int currentPage,
        int pageSize,
        boolean hasNext,
        @Schema(description = "마지막 요청 시각 내림차순(같으면 ID 내림차순)")
        List<AiReviewItem> aiReviews
) {

    public static AdminAiReviewListResponse from(ListResult result) {
        return new AdminAiReviewListResponse(
                result.currentPage(),
                result.pageSize(),
                result.hasNext(),
                result.aiReviews().stream().map(AiReviewItem::from).toList()
        );
    }

    @Schema(name = "AdminAiReviewListItem")
    public record AiReviewItem(
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
            @Schema(description = "이번 회차의 요청 시각. 목록 정렬 기준입니다.")
            Instant lastRequestedAt,
            @Schema(nullable = true)
            Instant completedAt,
            @Schema(
                    description = "작성자 앱에서 이 리뷰가 보이는지. 게시물이 승인됐고 게시물·주제·사진·작성자가 모두 삭제되지 않았을 때만 true입니다. 정지된 작성자는 영향을 주지 않습니다.")
            boolean visibleToAuthor
    ) {

        private static AiReviewItem from(AiReviewSummary summary) {
            return new AiReviewItem(
                    summary.aiReviewId(),
                    summary.status(),
                    summary.errorCode(),
                    summary.requestCount(),
                    summary.overallScore(),
                    PromptVersionResponse.from(summary.promptVersion()),
                    PostResponse.from(summary.post()),
                    AuthorResponse.from(summary.author()),
                    UsageResponse.from(summary.usage()),
                    summary.latencyMs(),
                    summary.requestedAt(),
                    summary.lastRequestedAt(),
                    summary.completedAt(),
                    summary.visibleToAuthor()
            );
        }
    }

    @Schema(name = "AdminAiReviewListPromptVersion")
    public record PromptVersionResponse(
            UUID promptVersionId,
            int version,
            String model
    ) {

        static PromptVersionResponse from(PromptVersionSummary promptVersion) {
            return new PromptVersionResponse(
                    promptVersion.promptVersionId(),
                    promptVersion.version(),
                    promptVersion.model()
            );
        }
    }

    @Schema(name = "AdminAiReviewListPost")
    public record PostResponse(
            UUID postId,
            @Schema(nullable = true)
            String thumbnailUrl,
            ModerationStatus moderationStatus,
            @Schema(nullable = true)
            Instant deletedAt
    ) {

        private static PostResponse from(PostSummary post) {
            return new PostResponse(
                    post.postId(),
                    post.thumbnailUrl(),
                    post.moderationStatus(),
                    post.deletedAt()
            );
        }
    }

    @Schema(name = "AdminAiReviewListAuthor")
    public record AuthorResponse(
            UUID userId,
            @Schema(nullable = true)
            String email,
            UserStatus status,
            @Schema(nullable = true)
            Instant deletedAt
    ) {

        static AuthorResponse from(AuthorSummary author) {
            return new AuthorResponse(
                    author.userId(),
                    author.email(),
                    author.status(),
                    author.deletedAt()
            );
        }
    }

    @Schema(name = "AdminAiReviewListUsage")
    public record UsageResponse(
            @Schema(nullable = true)
            Integer inputTokens,
            @Schema(nullable = true)
            Integer cachedInputTokens,
            @Schema(nullable = true)
            Integer outputTokens,
            @Schema(nullable = true)
            Integer reasoningTokens
    ) {

        static UsageResponse from(UsageSummary usage) {
            return new UsageResponse(
                    usage.inputTokens(),
                    usage.cachedInputTokens(),
                    usage.outputTokens(),
                    usage.reasoningTokens()
            );
        }
    }
}
