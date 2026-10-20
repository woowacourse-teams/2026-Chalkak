package com.chalkak.backend.aireview.service;

import com.chalkak.backend.aireview.domain.AiPromptVersion;

/**
 * 프롬프트 초안으로 사진 리뷰를 생성하는 포트. 실패하면 {@link AiReviewGenerationException}을 던진다.
 */
public interface AiReviewGenerator {

    AiReviewGeneration generate(AiReviewGenerationRequest request);

    /**
     * 이 생성기가 호출을 허용한 모델인지 확인한다. 허용되지 않은 모델은 호출 전에 거절하는 데 쓴다.
     */
    boolean supportsModel(String model);

    /**
     * 이 버전을 지금 서버가 호출할 수 있는지 확인한다. 서버가 해석하는 출력 스키마이고 허용된 모델이어야 한다.
     */
    default boolean canRun(AiPromptVersion version) {
        return version.hasCurrentOutputSchema() && supportsModel(version.getModel());
    }
}
