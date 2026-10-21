package com.chalkak.backend.aireview.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 사진 리뷰 프롬프트가 모델에게 요구하는 출력(스키마 버전 2). 종합 점수와 독특한 시각 여부는 모델이 아니라 서버가 계산한다.
 *
 * <p>
 * 모델 출력은 사용자 입력이 아니므로 검증 실패를 IllegalArgumentException으로 던지고, 어댑터가
 * INVALID_OUTPUT으로 변환한다. 관리자가 입력한 프롬프트 초안의 검증 실패는 BusinessException을 쓴다.
 */
public record PhotoReviewOutput(
        double compositionScore,
        double themeExpressionScore,
        AiReviewResult.Feedback feedback
) {

    private static final int SCHEMA_VERSION = 2;

    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 10;

    // OpenAI strict 스키마는 maxLength를 지원하지 않아 서버에서만 검사한다.
    private static final int MAX_SUMMARY_LENGTH = 60;
    private static final int MAX_FEEDBACK_SECTION_LENGTH = 150;
    private static final int SCORE_SCALE = 1;
    private static final String COMPOSITION_KEY = "COMPOSITION";
    private static final String COMPOSITION_LABEL = "구도";

    public PhotoReviewOutput {
        if (!(compositionScore >= MIN_SCORE && compositionScore <= MAX_SCORE)) {
            throw new IllegalArgumentException("구도 점수가 0 이상 10 이하가 아닙니다.");
        }
        if (!(themeExpressionScore >= MIN_SCORE && themeExpressionScore <= MAX_SCORE)) {
            throw new IllegalArgumentException("주제 표현 점수가 0 이상 10 이하가 아닙니다.");
        }
        if (feedback == null) {
            throw new IllegalArgumentException("피드백이 비어 있습니다.");
        }
        validateText(
                feedback.summary(),
                MAX_SUMMARY_LENGTH,
                "피드백 요약"
        );
        validateText(
                feedback.strength(),
                MAX_FEEDBACK_SECTION_LENGTH,
                "피드백 좋았던 점"
        );
        validateText(
                feedback.weakness(),
                MAX_FEEDBACK_SECTION_LENGTH,
                "피드백 아쉬운 점"
        );
        validateText(
                feedback.suggestion(),
                MAX_FEEDBACK_SECTION_LENGTH,
                "피드백 다음 제안"
        );
    }

    public static int schemaVersion() {
        return SCHEMA_VERSION;
    }

    public static int minScore() {
        return MIN_SCORE;
    }

    public static int maxScore() {
        return MAX_SCORE;
    }

    /**
     * 주제 표현 점수가 기준 이상이면 독특한 시각으로 판정한다. 원점수는 결과에 드러내지 않는다.
     */
    public AiReviewResult toResult(double distinctivePerspectiveThreshold) {
        double composition = round(compositionScore);
        // 현재 종합 점수는 구도 점수 하나로 정한다. 항목이 늘면 이 계산을 바꾼다.
        double overall = composition;
        return new AiReviewResult(
                overall,
                List.of(new AiReviewResult.Criterion(
                        COMPOSITION_KEY,
                        COMPOSITION_LABEL,
                        composition
                )),
                themeExpressionScore >= distinctivePerspectiveThreshold,
                feedback
        );
    }

    private static void validateText(
            String text,
            int maxLength,
            String name
    ) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(name + "이 비어 있습니다.");
        }
        if (text.codePointCount(0, text.length()) > maxLength) {
            throw new IllegalArgumentException(name + "이 최대 길이를 넘었습니다.");
        }
    }

    private static double round(double score) {
        return BigDecimal.valueOf(score)
                .setScale(SCORE_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
