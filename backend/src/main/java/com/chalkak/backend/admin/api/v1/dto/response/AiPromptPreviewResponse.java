package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.aireview.AdminAiPromptPreviewService.PreviewResult;
import com.chalkak.backend.aireview.domain.AiReviewResult;
import com.chalkak.backend.aireview.domain.PhotoReviewOutput;
import com.chalkak.backend.aireview.service.AiReviewGeneration.Usage;
import com.chalkak.backend.aireview.service.AiReviewGenerationException.FailureType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AiPromptPreviewResponse(
        @Schema(description = "생성에 성공했는지. OpenAI 실패도 200으로 돌려주며 이 값이 false입니다.")
        boolean succeeded,
        @Schema(description = "실패 유형. 성공하면 null입니다.", nullable = true)
        FailureType failureType,
        @Schema(description = "같은 요청을 다시 보내면 성공할 수 있는 실패인지. 성공하면 null입니다.", nullable = true)
        Boolean retryable,
        @Schema(description = "서버가 변환한 리뷰 결과. 실패하면 null입니다.", nullable = true)
        Result result,
        @Schema(description = "프롬프트 튜닝용 모델 원점수. 실패하면 null입니다.", nullable = true)
        Output output,
        @Schema(
                description = "OpenAI가 응답 본문을 준 경우의 토큰 사용량. HTTP 오류·시간 초과면 null이고, 항목 값이 null이면 OpenAI가 주지 않은 것입니다.",
                nullable = true)
        UsageResponse usage,
        @Schema(description = "OpenAI 호출 지연시간(ms). 응답 본문을 받지 못하면 null입니다.", nullable = true)
        Long latencyMs,
        @Schema(description = "OpenAI 응답 ID. 응답 본문을 받지 못하면 null입니다.", nullable = true)
        String responseId
) {

    public static AiPromptPreviewResponse from(PreviewResult preview) {
        return new AiPromptPreviewResponse(
                preview.succeeded(),
                preview.failureType(),
                preview.retryable(),
                preview.result() == null ? null : Result.from(preview.result()),
                preview.output() == null ? null : Output.from(preview.output()),
                preview.usage() == null ? null : UsageResponse.from(preview.usage()),
                preview.latencyMs(),
                preview.responseId()
        );
    }

    public record Result(
            double overallScore,
            List<Criterion> criteria,
            boolean distinctivePerspective,
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

    public record Criterion(
            String key,
            String label,
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

    public record Output(double compositionScore, double themeExpressionScore) {

        private static Output from(PhotoReviewOutput output) {
            return new Output(output.compositionScore(), output.themeExpressionScore());
        }
    }

    public record UsageResponse(
            Integer inputTokens,
            Integer cachedInputTokens,
            Integer outputTokens,
            Integer reasoningTokens
    ) {

        private static UsageResponse from(Usage usage) {
            return new UsageResponse(
                    usage.inputTokens(),
                    usage.cachedInputTokens(),
                    usage.outputTokens(),
                    usage.reasoningTokens()
            );
        }
    }
}
