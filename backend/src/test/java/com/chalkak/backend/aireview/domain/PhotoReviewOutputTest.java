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
        PhotoReviewOutput output = new PhotoReviewOutput(
                8.72,
                3.0,
                feedback("구도가 안정적입니다.")
        );

        // When
        AiReviewResult result = output.toResult(7.0);

        // Then
        assertThat(result.overallScore()).isEqualTo(8.7);
        assertThat(result.criteria()).containsExactly(
                new AiReviewResult.Criterion(
                        "COMPOSITION",
                        "구도",
                        8.7
                ));
        assertThat(result.feedback()).isEqualTo(feedback("구도가 안정적입니다."));
    }

    @ParameterizedTest
    @CsvSource({"8.75, 8.8", "8.74, 8.7", "8.85, 8.9", "0.15, 0.2", "0, 0.0", "10, 10.0"})
    @DisplayName("변환_점수를_소수 첫째 자리로 반올림한다")
    void toResult_score_roundsHalfUpToOneDecimal(double score, double expected) {
        // When
        AiReviewResult result = new PhotoReviewOutput(
                score,
                5,
                feedback("피드백")
        ).toResult(7.0);

        // Then
        assertThat(result.overallScore()).isEqualTo(expected);
        assertThat(result.criteria().get(0).score()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"6.99, false", "7.0, true", "8, true"})
    @DisplayName("변환_주제 표현 점수가 기준 7.0 이상이면_독특한 시각이다")
    void toResult_themeExpressionScore_comparedWithThreshold(double themeScore, boolean expected) {
        // When
        AiReviewResult result = new PhotoReviewOutput(
                7,
                themeScore,
                feedback("피드백")
        ).toResult(7.0);

        // Then
        assertThat(result.distinctivePerspective()).isEqualTo(expected);
    }

    @Test
    @DisplayName("변환_기준이 바뀌면_같은 점수도 판정이 달라진다")
    void toResult_customThreshold_changesJudgement() {
        // Given
        PhotoReviewOutput output = new PhotoReviewOutput(
                7,
                6.5,
                feedback("피드백")
        );

        // When & Then
        assertThat(output.toResult(6.5).distinctivePerspective()).isTrue();
        assertThat(output.toResult(6.6).distinctivePerspective()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 10})
    @DisplayName("생성_구도 점수가 0 또는 10이면_성공한다")
    void create_boundaryComposition_succeeds(double score) {
        // When & Then
        assertThatCode(() -> new PhotoReviewOutput(
                score,
                5,
                feedback("피드백")
        ))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 10.01, Double.NaN, Double.POSITIVE_INFINITY})
    @DisplayName("생성_구도 점수가 범위를 벗어나면_거부한다")
    void create_outOfRangeComposition_throwsIllegalArgumentException(double score) {
        // When & Then
        assertThatThrownBy(() -> new PhotoReviewOutput(
                score,
                5,
                feedback("피드백")
        ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 7.5, 10})
    @DisplayName("생성_주제 표현 점수가 0 이상 10 이하면_성공한다")
    void create_boundaryThemeExpression_succeeds(double score) {
        // When & Then
        assertThatCode(() -> new PhotoReviewOutput(
                5,
                score,
                feedback("피드백")
        ))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 10.01, Double.NaN, Double.POSITIVE_INFINITY})
    @DisplayName("생성_주제 표현 점수가 범위를 벗어나면_거부한다")
    void create_outOfRangeThemeExpression_throwsIllegalArgumentException(double score) {
        // When & Then
        assertThatThrownBy(() -> new PhotoReviewOutput(
                5,
                score,
                feedback("피드백")
        ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    @DisplayName("생성_요약이 60자(코드포인트)면_성공하고 61자면_거부한다")
    void create_summaryLength_boundary(String unit) {
        assertThatCode(() -> output(new AiReviewResult.Feedback(
                unit.repeat(60),
                "좋음",
                "아쉬움",
                "제안"
        )))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> output(new AiReviewResult.Feedback(
                unit.repeat(61),
                "좋음",
                "아쉬움",
                "제안"
        )))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"가, strength", "😀, strength", "가, weakness", "😀, weakness", "가, suggestion",
            "😀, suggestion"})
    @DisplayName("생성_섹션이 150자(코드포인트)면_성공하고 151자면_거부한다")
    void create_sectionLength_boundary(String unit, String section) {
        assertThatCode(() -> output(withSection(section, unit.repeat(150))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> output(withSection(section, unit.repeat(151))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n"})
    @DisplayName("생성_피드백 항목이 비어 있으면_거부한다")
    void create_blankSection_throwsIllegalArgumentException(String blank) {
        for (String section : new String[]{"summary", "strength", "weakness", "suggestion"}) {
            assertThatThrownBy(() -> output(withSection(section, blank)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("생성_피드백이 null이면_거부한다")
    void create_nullFeedback_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> output(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AiReviewResult.Feedback feedback(String summary) {
        return new AiReviewResult.Feedback(
                summary,
                "좋음",
                "아쉬움",
                "제안"
        );
    }

    private static AiReviewResult.Feedback withSection(String section, String value) {
        return new AiReviewResult.Feedback(
                "summary".equals(section) ? value : "요약",
                "strength".equals(section) ? value : "좋음",
                "weakness".equals(section) ? value : "아쉬움",
                "suggestion".equals(section) ? value : "제안"
        );
    }

    private static PhotoReviewOutput output(AiReviewResult.Feedback feedback) {
        return new PhotoReviewOutput(
                5,
                5,
                feedback
        );
    }
}
