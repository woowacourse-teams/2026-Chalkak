package com.chalkak.backend.aireview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.aireview.domain.AiPromptParameters.ImageDetail;
import com.chalkak.backend.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiPromptParametersTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 16000})
    @DisplayName("생성_maxOutputTokens가 경계값이면_성공한다")
    void create_boundaryMaxOutputTokens_succeeds(int maxOutputTokens) {
        // When & Then
        assertThatCode(() -> new AiPromptParameters(ImageDetail.LOW, maxOutputTokens, null))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 16001})
    @DisplayName("생성_maxOutputTokens가 범위를 벗어나면_거부한다")
    void create_outOfRangeMaxOutputTokens_throwsBusinessException(int maxOutputTokens) {
        // When & Then
        assertThatThrownBy(() -> new AiPromptParameters(ImageDetail.LOW, maxOutputTokens, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("생성_imageDetail이 null이면_HIGH로 정한다")
    void create_nullImageDetail_defaultsToHigh() {
        // When
        AiPromptParameters parameters = new AiPromptParameters(null, 500, null);

        // Then
        assertThat(parameters.imageDetail()).isEqualTo(ImageDetail.HIGH);
    }

    @Test
    @DisplayName("생성_reasoningEffort가 null이면_허용한다")
    void create_nullReasoningEffort_isAllowed() {
        // When
        AiPromptParameters parameters = new AiPromptParameters(ImageDetail.HIGH, 500, null);

        // Then
        assertThat(parameters.reasoningEffort()).isNull();
    }
}
