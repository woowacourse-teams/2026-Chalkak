package com.chalkak.backend.aireview.service;

/**
 * 프롬프트 초안으로 사진 리뷰를 생성하는 포트. 실패하면 {@link AiReviewGenerationException}을 던진다.
 */
public interface AiReviewGenerator {

    AiReviewGeneration generate(AiReviewGenerationRequest request);
}
