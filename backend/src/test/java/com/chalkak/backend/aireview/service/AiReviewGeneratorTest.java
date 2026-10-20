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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @ParameterizedTest
    @CsvSource({
            "gpt-6.1-sol, 1, true",
            "gpt-6.1-sol, 0, false",
            "gpt-unknown, 1, false"
    })
    @DisplayName("현재 출력 스키마이고 허용된 모델일 때만 실행할 수 있다")
    void canRun_byOutputSchemaAndModel(
            String model,
            String outputSchemaVersion,
            boolean expected
    ) {
        // Given
        AiPromptVersion version = version(model);
        ReflectionTestUtils.setField(
                version,
                "outputSchemaVersion",
                outputSchemaVersion
        );

        // When & Then
        assertThat(generator.canRun(version)).isEqualTo(expected);
    }

    private AiPromptVersion version(String model) {
        AiPromptDraft draft = new AiPromptDraft(
                AiPromptType.PHOTO_REVIEW,
                "시스템",
                "주제는 {{topicTitle}}",
                model,
                new AiPromptParameters(
                        ImageDetail.LOW,
                        700,
                        ReasoningEffort.HIGH,
                        6.5
                )
        );
        return AiPromptVersion.create(
                draft,
                1,
                null,
                UUID.randomUUID()
        );
    }
}
