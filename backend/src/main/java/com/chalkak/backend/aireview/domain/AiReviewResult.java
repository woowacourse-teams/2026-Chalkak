package com.chalkak.backend.aireview.domain;

import java.util.List;

/**
 * 모델 출력을 서비스 규칙으로 변환한 사진 리뷰 결과.
 */
public record AiReviewResult(
        double overallScore,
        List<Criterion> criteria,
        boolean distinctivePerspective,
        Feedback feedback
) {

    /**
     * 앱이 화면에 나눠 보여주는 피드백. 길이·공백 검증은 모델 출력을 받는 PhotoReviewOutput이 하고, 저장값을 다시 읽을 때는
     * 검증하지 않는다.
     */
    public record Feedback(
            String summary,
            String strength,
            String weakness,
            String suggestion
    ) {
    }

    public record Criterion(
            String key,
            String label,
            double score
    ) {
    }
}
