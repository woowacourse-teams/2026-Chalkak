package com.chalkak.backend.jev.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.jev.repository.JevMetricsRepository.Call;
import com.chalkak.backend.jev.repository.JevMetricsRepository.Sample;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JevMetricsTest {

    @Test
    @DisplayName("최종 정답을 질문 정답으로 복사하지 않고 오승인과 오반려 분모를 구분한다")
    void calculate_goldLabels_doNotInferQuestionLabels() {
        var metrics = JevMetrics.calculate(
                List.of(sample(
                        JevDecision.APPROVE,
                        JevDecision.REJECT,
                        "one"
                )),
                List.of(),
                false
        );
        assertThat(metrics.finalAgreement().rated()).isEqualTo(1);
        assertThat(metrics.finalAgreement().falseApprovals()).isEqualTo(1);
        assertThat(metrics.falseAutoApprovals()).isEqualTo(1);
        assertThat(metrics.questions().get("topic_fit").rated()).isZero();
        assertThat(metrics.consistencyPercent()).isNull();
    }

    @Test
    @DisplayName("같은 입력 정상 결과 다섯 번이 있어야 일관성 그룹을 계산한다")
    void calculate_repeatedInput_countsStableGroups() {
        var rows = IntStream.range(0, 5).mapToObj(i -> sample(
                JevDecision.APPROVE,
                JevDecision.APPROVE,
                "same-input"
        )).toList();
        var metrics = JevMetrics.calculate(
                rows,
                List.of(),
                false
        );
        assertThat(metrics.repeatedGroups()).isEqualTo(1);
        assertThat(metrics.consistencyPercent()).isEqualTo(100.0);
        assertThat(JevMetrics.calculate(
                rows.subList(0, 4),
                List.of(),
                false
        ).consistencyPercent()).isNull();
    }

    @Test
    @DisplayName("HTTP p99는 개별 왕복 nearest-rank이고 없는 토큰/시간을 0으로 만들지 않는다")
    void calculate_usage_preservesMissingAndMeasuresHttpOnly() {
        var calls = IntStream.rangeClosed(1, 100).mapToObj(i -> new Call(
                UUID.randomUUID(),
                null,
                200,
                (long) i,
                10L,
                null
        )).toList();
        var metrics = JevMetrics.calculate(
                List.of(),
                calls,
                false
        );
        assertThat(metrics.usage().httpP99Ms()).isEqualTo(99);
        assertThat(metrics.usage().knownInputTokens()).isEqualTo(1000);
        assertThat(metrics.usage().unknownOutputUsageCalls()).isEqualTo(100);
        assertThat(metrics.finalAgreement().matchPercent()).isNull();
    }

    private Sample sample(
            JevDecision actual,
            JevDecision expected,
            String repeatKey
    ) {
        return new Sample(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                false,
                JevDisposition.AUTO_APPROVED,
                new JevEvaluation(
                        Map.of(),
                        Map.of("topic_fit", actual),
                        actual,
                        null,
                        null,
                        null,
                        null
                ),
                new JevGoldLabel(
                        expected,
                        Map.of(),
                        "정답 근거",
                        JevGoldLabel.Sampling.RANDOM,
                        UUID.randomUUID(),
                        Instant.parse("2026-11-20T01:00:00Z")
                ),
                repeatKey
        );
    }
}
