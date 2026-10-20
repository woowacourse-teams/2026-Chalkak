package com.chalkak.backend.aireview.service;

import com.chalkak.backend.aireview.domain.AiPromptDraft;

public record AiReviewGenerationRequest(
        AiPromptDraft draft,
        String imageUrl,
        String topicTitle
) {

    public AiReviewGenerationRequest {
        if (draft == null) {
            throw new IllegalArgumentException("프롬프트 초안이 필요합니다.");
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("이미지 주소가 필요합니다.");
        }
        if (topicTitle == null || topicTitle.isBlank()) {
            throw new IllegalArgumentException("주제 제목이 필요합니다.");
        }
    }
}
