package com.chalkak.backend.jev.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.jev.domain.JevDecision;
import com.chalkak.backend.jev.domain.JevPromptFixtures;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class JevResponseParserTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final JevResponseParser parser = new JevResponseParser(mapper);

    @ParameterizedTest
    @CsvSource({
            "approve,0.6,0.3,0.1,APPROVE",
            "approve,0.59,0.3,0.11,HUMAN_REVIEW",
            "approve,0.6,0.31,0.09,HUMAN_REVIEW",
            "reject,0.25,0.15,0.6,REJECT",
            "reject,0.24,0.16,0.6,HUMAN_REVIEW",
            "reject,0.3,0.11,0.59,HUMAN_REVIEW",
            "human_review,0.3,0.5,0.2,HUMAN_REVIEW"
    })
    @DisplayName("기존 60퍼센트 후처리 경계와 모델 직접 검토를 유지한다")
    void parse_thresholdBoundary_keepsFrozenPolicy(
            String choice,
            double approve,
            double review,
            double reject,
            JevDecision expected
    ) {
        // Given
        Map<String, Object> answers = allAnswers(answer(
                choice,
                approve,
                review,
                reject
        ));
        // When
        var result = parser.parse(
                response(answers),
                "jev-1.14.0",
                JevPromptFixtures.photo().questions()
        );
        // Then
        assertThat(result.error()).isNull();
        assertThat(result.recommendation()).isEqualTo(expected);
        assertThat(result.inputTokens()).isEqualTo(100);
        assertThat(result.outputTokens()).isEqualTo(20);
    }

    @Test
    @DisplayName("일부 반려가 있어도 질문이 누락되면 기술 오류 검토다")
    void parse_missingQuestion_prioritizesTechnicalReview() {
        // Given
        Map<String, Object> answers = allAnswers(answer(
                "reject",
                0,
                0,
                1
        ));
        answers.remove("image_expression");
        // When
        var result = parser.parse(
                response(answers),
                "jev-1.14.0",
                JevPromptFixtures.photo().questions()
        );
        // Then
        assertThat(result.recommendation()).isEqualTo(JevDecision.HUMAN_REVIEW);
        assertThat(result.error()).isEqualTo("MISSING_OR_INVALID_QUESTIONS");
        assertThat(result.inputTokens()).isEqualTo(100);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "null", "\"bad\"", "\"0.8\"", "-0.1", "1.1"})
    @DisplayName("확률 형식과 범위 오류는 승인으로 취급하지 않는다")
    void parse_invalidProbability_returnsReview(String probability) {
        // Given
        String raw = response(allAnswers(answer(
                "approve",
                0.8,
                0.1,
                0.1
        ))).replace("0.8", probability);
        // When
        var result = parser.parse(
                raw,
                "jev-1.14.0",
                JevPromptFixtures.photo().questions()
        );
        // Then
        assertThat(result.recommendation()).isEqualTo(JevDecision.HUMAN_REVIEW);
        assertThat(result.error()).isNotNull();
    }

    @Test
    @DisplayName("세 질문은 평균내지 않고 반려 추천 하나를 보존한다")
    void parse_oneReject_doesNotAverage() {
        // Given
        Map<String, Object> answers = allAnswers(answer(
                "approve",
                1,
                0,
                0
        ));
        answers.put("image_expression", answer(
                "reject",
                0,
                0,
                1
        ));
        // When
        var result = parser.parse(
                response(answers),
                "jev-1.14.0",
                JevPromptFixtures.photo().questions()
        );
        // Then
        assertThat(result.recommendation()).isEqualTo(JevDecision.REJECT);
        assertThat(result.decisions().get("topic_fit")).isEqualTo(JevDecision.APPROVE);
    }

    @Test
    @DisplayName("응답 자체가 없으면 사용량은 0이 아니라 확인 불가다")
    void parse_noResponse_returnsNullUsage() {
        // When
        var result = parser.parse(
                "not-json",
                "jev-1.14.0",
                JevPromptFixtures.photo().questions()
        );
        // Then
        assertThat(result.inputTokens()).isNull();
        assertThat(result.outputTokens()).isNull();
        assertThat(result.error()).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({"0.17,true", "0.169,false"})
    @DisplayName("동결 실험의 확률 합계 3퍼센트 반올림 허용 오차를 유지한다")
    void parse_roundedSum_preservesExistingTolerance(double reject, boolean valid) {
        // Given
        String raw = response(allAnswers(answer(
                "approve",
                0.6,
                0.2,
                reject
        )));
        // When
        var result = parser.parse(
                raw,
                "jev-1.14.0",
                JevPromptFixtures.photo().questions()
        );
        // Then
        assertThat(result.error() == null).isEqualTo(valid);
    }

    @Test
    @DisplayName("Confidence 미제공은 기존 방식대로 null로 보존하며 정상 점수를 버리지 않는다")
    void parse_missingConfidence_preservesUnknownValue() {
        // Given
        String raw = response(allAnswers(answer(
                "approve",
                0.8,
                0.1,
                0.1
        ))).replace("\"confidence\":0.9", "\"confidence\":null");
        // When
        var result = parser.parse(
                raw,
                "jev-1.14.0",
                JevPromptFixtures.photo().questions()
        );
        // Then
        assertThat(result.error()).isNull();
        assertThat(result.answers().get("topic_fit").confidence()).isNull();
    }

    private Map<String, Object> allAnswers(Map<String, Object> answer) {
        Map<String, Object> result = new LinkedHashMap<>();
        JevPromptFixtures.photo().questions().keySet().forEach(key -> result.put(key, answer));
        return result;
    }

    private Map<String, Object> answer(
            String choice,
            double approve,
            double review,
            double reject
    ) {
        return Map.of(
                "type",
                "choice",
                "choice",
                choice,
                "confidence",
                0.9,
                "probabilities",
                Map.of(
                        "approve",
                        approve,
                        "human_review",
                        review,
                        "reject",
                        reject
                )
        );
    }

    private String response(Map<String, Object> answers) {
        return mapper.writeValueAsString(Map.of(
                "model",
                "jev-1.14.0",
                "answers",
                answers,
                "usage",
                Map.of(
                        "input_tokens",
                        100,
                        "output_tokens",
                        20
                )
        ));
    }
}
