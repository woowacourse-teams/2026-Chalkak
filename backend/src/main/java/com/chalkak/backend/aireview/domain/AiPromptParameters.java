package com.chalkak.backend.aireview.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;

/**
 * 모델 호출 옵션. reasoningEffort가 null이면 모델 기본값을 쓰도록 전달하지 않는다.
 * distinctivePerspectiveThreshold는 소수인 주제 표현 점수와 그대로 비교하는, 프롬프트 버전별 설정값(나중에 버전과 함께 저장한다)이다.
 * 점수가 이 값 이상이면 독특한 시각으로 본다. null이면 기본값 7.0으로 정하므로 생성된 뒤에는 항상 null이 아니다.
 */
public record AiPromptParameters(
        ImageDetail imageDetail,
        int maxOutputTokens,
        ReasoningEffort reasoningEffort,
        Double distinctivePerspectiveThreshold
) {

    private static final int MIN_OUTPUT_TOKENS = 1;
    private static final int MAX_OUTPUT_TOKENS = 16000;
    private static final double DEFAULT_THRESHOLD = 7.0;
    private static final double MIN_THRESHOLD = 0;
    private static final double MAX_THRESHOLD = 10;

    public AiPromptParameters {
        if (maxOutputTokens < MIN_OUTPUT_TOKENS || maxOutputTokens > MAX_OUTPUT_TOKENS) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "최대 출력 토큰은 1 이상 16000 이하여야 합니다.");
        }
        if (distinctivePerspectiveThreshold == null) {
            distinctivePerspectiveThreshold = DEFAULT_THRESHOLD;
        }
        if (!(distinctivePerspectiveThreshold >= MIN_THRESHOLD
                && distinctivePerspectiveThreshold <= MAX_THRESHOLD)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "독특한 시각 판정 기준은 0 이상 10 이하여야 합니다.");
        }
        if (imageDetail == null) {
            imageDetail = ImageDetail.HIGH;
        }
    }

    public enum ImageDetail {
        LOW, HIGH, AUTO
    }

    public enum ReasoningEffort {
        LOW, MEDIUM, HIGH
    }
}
