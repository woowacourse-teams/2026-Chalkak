package com.chalkak.backend.aireview.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.aireview.domain.AiPromptDraft;
import com.chalkak.backend.aireview.domain.AiPromptParameters;
import com.chalkak.backend.aireview.domain.AiPromptType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AiReviewGenerationRequestTest {

    private static final AiPromptDraft DRAFT = new AiPromptDraft(
            AiPromptType.PHOTO_REVIEW,
            "사진을 평가한다.",
            "주제: {{topicTitle}}",
            "gpt-5-mini",
            new AiPromptParameters(null, 500, null));

    @Test
    @DisplayName("생성_모든 값이 있으면_성공한다")
    void create_validValues_succeeds() {
        // When & Then
        assertThatCode(() -> new AiReviewGenerationRequest(DRAFT, "https://cdn.test/a.jpg", "노을"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("생성_draft가 null이면_거부한다")
    void create_nullDraft_throwsIllegalArgumentException() {
        // When & Then
        assertThatThrownBy(() -> new AiReviewGenerationRequest(null, "https://cdn.test/a.jpg", "노을"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("생성_imageUrl이 비어 있으면_거부한다")
    void create_blankImageUrl_throwsIllegalArgumentException(String imageUrl) {
        // When & Then
        assertThatThrownBy(() -> new AiReviewGenerationRequest(DRAFT, imageUrl, "노을"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("생성_topicTitle이 비어 있으면_거부한다")
    void create_blankTopicTitle_throwsIllegalArgumentException(String topicTitle) {
        // When & Then
        assertThatThrownBy(() -> new AiReviewGenerationRequest(DRAFT, "https://cdn.test/a.jpg", topicTitle))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
