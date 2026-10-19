package com.chalkak.backend.aireview.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;

/**
 * 모델 호출 옵션. reasoningEffort가 null이면 모델 기본값을 쓰도록 전달하지 않는다.
 */
public record AiPromptParameters(
        ImageDetail imageDetail,
        int maxOutputTokens,
        ReasoningEffort reasoningEffort
) {

    private static final int MIN_OUTPUT_TOKENS = 1;
    private static final int MAX_OUTPUT_TOKENS = 16000;

    public AiPromptParameters {
        if (maxOutputTokens < MIN_OUTPUT_TOKENS || maxOutputTokens > MAX_OUTPUT_TOKENS) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "최대 출력 토큰은 1 이상 16000 이하여야 합니다.");
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
