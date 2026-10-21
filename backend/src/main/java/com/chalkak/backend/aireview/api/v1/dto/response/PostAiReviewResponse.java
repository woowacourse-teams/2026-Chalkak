package com.chalkak.backend.aireview.api.v1.dto.response;

import com.chalkak.backend.aireview.domain.AiReviewResult;
import com.chalkak.backend.aireview.service.PostAiReviewService.ApiStatus;
import com.chalkak.backend.aireview.service.PostAiReviewService.ReviewResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record PostAiReviewResponse(
        @Schema(
                description = "리뷰 진행 상태. 앱은 HTTP 상태 코드가 아니라 이 값으로 분기한다. NONE은 아직 요청하지 않은 상태다.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        ApiStatus status,
        @Schema(description = "최초 요청 시각(UTC). 요청 전(NONE)에는 null", nullable = true)
        Instant requestedAt,
        @Schema(description = "완료 또는 실패한 시각(UTC). 요청 전·처리 중에는 null", nullable = true)
        Instant completedAt,
        @Schema(description = "리뷰 결과. COMPLETED일 때만 값이 있고 그 밖에는 null", nullable = true)
        Result result,
        @Schema(
                description = "실패한 리뷰를 다시 요청할 수 있는지. 실패(FAILED)이고 요청 횟수(처음 포함 최대 3번)가 남았을 때만 true이며 그 밖의 상태는 false다. 횟수만 반영하므로 true여도 AI 설정이 없으면 400, 이용이 정지된 회원이면 403이 될 수 있다.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean canRequestAgain
) {

    public static PostAiReviewResponse from(ReviewResult reviewResult) {
        return new PostAiReviewResponse(
                reviewResult.status(),
                reviewResult.requestedAt(),
                reviewResult.completedAt(),
                reviewResult.result() == null ? null : Result.from(reviewResult.result()),
                reviewResult.canRequestAgain()
        );
    }

    @Schema(name = "PostAiReviewResult")
    public record Result(
            @Schema(description = "종합 점수(0~10, 소수 첫째 자리)")
            double overallScore,
            List<Criterion> criteria,
            @Schema(description = "독특한 시각이 드러났는지 여부")
            boolean distinctivePerspective,
            Feedback feedback
    ) {

        private static Result from(AiReviewResult result) {
            return new Result(
                    result.overallScore(),
                    result.criteria().stream().map(Criterion::from).toList(),
                    result.distinctivePerspective(),
                    Feedback.from(result.feedback())
            );
        }
    }

    @Schema(name = "PostAiReviewFeedback")
    public record Feedback(
            @Schema(description = "한 줄 요약(60자 이하). 화면의 굵은 제목으로 쓴다.")
            String summary,
            @Schema(description = "좋았던 점(150자 이하)")
            String strength,
            @Schema(description = "아쉬운 점(150자 이하)")
            String weakness,
            @Schema(description = "다음엔 이렇게, 다음 촬영을 위한 제안(150자 이하)")
            String suggestion
    ) {

        private static Feedback from(AiReviewResult.Feedback feedback) {
            return new Feedback(
                    feedback.summary(),
                    feedback.strength(),
                    feedback.weakness(),
                    feedback.suggestion()
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
