package com.chalkak.backend.aireview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PhotoReviewOutputTest {

    @Test
    @DisplayName("변환_구도 점수 8.72면_종합 점수와 구도 항목이 8.7이다")
    void toResult_compositionScore_setsOverallAndCriterion() {
        // Given
        PhotoReviewOutput output = new PhotoReviewOutput(8.72, false, "구도가 안정적입니다.");

        // When
        AiReviewResult result = output.toResult();

        // Then
        assertThat(result.overallScore()).isEqualTo(8.7);
        assertThat(result.criteria()).containsExactly(
                new AiReviewResult.Criterion("COMPOSITION", "구도", 8.7));
        assertThat(result.feedback()).isEqualTo("구도가 안정적입니다.");
    }

    @ParameterizedTest
    @CsvSource({"8.75, 8.8", "8.74, 8.7", "8.85, 8.9", "0.15, 0.2", "0, 0.0", "10, 10.0"})
    @DisplayName("변환_점수를_소수 첫째 자리로 반올림한다")
    void toResult_score_roundsHalfUpToOneDecimal(double score, double expected) {
        // When
        AiReviewResult result = new PhotoReviewOutput(score, false, "피드백").toResult();

        // Then
        assertThat(result.overallScore()).isEqualTo(expected);
        assertThat(result.criteria().get(0).score()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 10})
    @DisplayName("생성_점수가 0 또는 10이면_성공한다")
    void create_boundaryScore_succeeds(double score) {
        // When & Then
        assertThatCode(() -> new PhotoReviewOutput(score, false, "피드백"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 10.01, Double.NaN, Double.POSITIVE_INFINITY})
    @DisplayName("생성_점수가 범위를 벗어나면_거부한다")
    void create_outOfRangeScore_throwsIllegalArgumentException(double score) {
        // When & Then
        assertThatThrownBy(() -> new PhotoReviewOutput(score, false, "피드백"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    @DisplayName("생성_feedback이 300자(코드포인트)면_성공하고 301자면_거부한다")
    void create_feedbackLength_boundary(String unit) {
        // When & Then
        assertThatCode(() -> new PhotoReviewOutput(5, false, unit.repeat(300)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new PhotoReviewOutput(5, false, unit.repeat(301)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n"})
    @DisplayName("생성_feedback이 비어 있으면_거부한다")
    void create_blankFeedback_throwsIllegalArgumentException(String feedback) {
        // When & Then
        assertThatThrownBy(() -> new PhotoReviewOutput(5, false, feedback))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("변환_distinctivePerspective를_그대로 전달한다")
    void toResult_distinctivePerspective_passesThrough(boolean distinctive) {
        // When
        AiReviewResult result = new PhotoReviewOutput(7, distinctive, "피드백").toResult();

        // Then
        assertThat(result.distinctivePerspective()).isEqualTo(distinctive);
    }
}
