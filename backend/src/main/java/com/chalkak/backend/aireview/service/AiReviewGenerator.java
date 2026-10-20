package com.chalkak.backend.aireview.service;

/**
 * 프롬프트 초안으로 사진 리뷰를 생성하는 포트. 실패하면 {@link AiReviewGenerationException}을 던진다.
 */
public interface AiReviewGenerator {

    AiReviewGeneration generate(AiReviewGenerationRequest request);

    /**
     * 이 생성기가 호출을 허용한 모델인지 확인한다. 허용되지 않은 모델은 호출 전에 거절하는 데 쓴다.
     */
    boolean supportsModel(String model);
}
