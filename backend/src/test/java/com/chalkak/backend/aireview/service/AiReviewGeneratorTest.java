package com.chalkak.backend.aireview.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.aireview.domain.AiPromptDraft;
import com.chalkak.backend.aireview.domain.AiPromptParameters;
import com.chalkak.backend.aireview.domain.AiPromptParameters.ImageDetail;
import com.chalkak.backend.aireview.domain.AiPromptParameters.ReasoningEffort;
import com.chalkak.backend.aireview.domain.AiPromptType;
import com.chalkak.backend.aireview.domain.AiPromptVersion;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AiReviewGeneratorTest {

    private static final String SUPPORTED_MODEL = "gpt-6.1-sol";

    private final AiReviewGenerator generator = new AiReviewGenerator() {
        @Override
        public AiReviewGeneration generate(AiReviewGenerationRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean supportsModel(String model) {
            return SUPPORTED_MODEL.equals(model);
        }
    };

    @Test
    @DisplayName("현재 출력 스키마이고 허용된 모델이면 실행할 수 있다")
    void canRun_currentSchemaAndSupportedModel_returnsTrue() {
        // Given
        AiPromptVersion version = version(SUPPORTED_MODEL);

        // When & Then
        assertThat(generator.canRun(version)).isTrue();
    }

    @Test
    @DisplayName("출력 스키마가 현재 버전과 다르면 허용된 모델이어도 실행할 수 없다")
    void canRun_schemaMismatch_returnsFalse() {
        // Given
        AiPromptVersion version = version(SUPPORTED_MODEL);
        ReflectionTestUtils.setField(version, "outputSchemaVersion", "0");

        // When & Then
        assertThat(generator.canRun(version)).isFalse();
    }

    @Test
    @DisplayName("허용되지 않은 모델이면 현재 출력 스키마여도 실행할 수 없다")
    void canRun_unsupportedModel_returnsFalse() {
        // Given
        AiPromptVersion version = version("gpt-unknown");

        // When & Then
        assertThat(generator.canRun(version)).isFalse();
    }

    private AiPromptVersion version(String model) {
        AiPromptDraft draft = new AiPromptDraft(
                AiPromptType.PHOTO_REVIEW, "시스템", "주제는 {{topicTitle}}", model,
                new AiPromptParameters(ImageDetail.LOW, 700, ReasoningEffort.HIGH, 6.5));
        return AiPromptVersion.create(draft, 1, null, UUID.randomUUID());
    }
}
