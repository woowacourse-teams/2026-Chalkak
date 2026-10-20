package com.chalkak.backend.aireview.domain;

import java.util.List;

/**
 * 모델 출력을 서비스 규칙으로 변환한 사진 리뷰 결과.
 */
public record AiReviewResult(
        double overallScore,
        List<Criterion> criteria,
        boolean distinctivePerspective,
        String feedback
) {

    public record Criterion(
            String key,
            String label,
            double score
    ) {
    }
}
