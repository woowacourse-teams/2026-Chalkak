package com.chalkak.backend.aireview.api.v1.dto.response;

import com.chalkak.backend.aireview.domain.AiReviewResult;
import com.chalkak.backend.aireview.service.PostAiReviewService.ApiStatus;
import com.chalkak.backend.aireview.service.PostAiReviewService.ReviewResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record PostAiReviewResponse(
        @Schema(
                description = "리뷰 진행 상태. 앱은 HTTP 상태 코드가 아니라 이 값으로 분기한다.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        ApiStatus status,
        @Schema(description = "최초 요청 시각(UTC)", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant requestedAt,
        @Schema(description = "완료 또는 실패한 시각(UTC). 완료·실패 전에는 null", nullable = true)
        Instant completedAt,
        @Schema(description = "리뷰 결과. COMPLETED일 때만 값이 있고 그 밖에는 null", nullable = true)
        Result result
) {

    public static PostAiReviewResponse from(ReviewResult reviewResult) {
        return new PostAiReviewResponse(
                reviewResult.status(),
                reviewResult.requestedAt(),
                reviewResult.completedAt(),
                reviewResult.result() == null ? null : Result.from(reviewResult.result())
        );
    }

    @Schema(name = "PostAiReviewResult")
    public record Result(
            @Schema(description = "종합 점수(0~10, 소수 첫째 자리)")
            double overallScore,
            List<Criterion> criteria,
            @Schema(description = "독특한 시각이 드러났는지 여부")
            boolean distinctivePerspective,
            @Schema(description = "피드백(300자 이하)")
            String feedback
    ) {

        private static Result from(AiReviewResult result) {
            return new Result(
                    result.overallScore(),
                    result.criteria().stream().map(Criterion::from).toList(),
                    result.distinctivePerspective(),
                    result.feedback()
            );
        }
    }

    @Schema(name = "PostAiReviewCriterion")
    public record Criterion(
            @Schema(description = "평가 항목 식별자", example = "COMPOSITION")
            String key,
            @Schema(description = "평가 항목 이름", example = "구도")
            String label,
            @Schema(description = "항목 점수(0~10, 소수 첫째 자리)")
            double score
    ) {

        private static Criterion from(AiReviewResult.Criterion criterion) {
            return new Criterion(
                    criterion.key(),
                    criterion.label(),
                    criterion.score()
            );
        }
    }
}
