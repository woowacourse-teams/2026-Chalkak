package com.chalkak.backend.jev.domain;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record JevChoiceAnswer(
        String type,
        String choice,
        BigDecimal confidence,
        Map<String, BigDecimal> probabilities
) {

    public JevChoiceAnswer {
        probabilities = probabilities == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(probabilities));
    }

    public void validate(Set<String> options) {
        if (!"choice".equals(type) || choice == null || !options.contains(choice)
                || (confidence != null && !isProbability(confidence))
                || !probabilities.keySet().equals(options)
                || probabilities.values().stream().anyMatch(value -> !isProbability(value))) {
            throw new IllegalArgumentException("INVALID_CHOICE_RESPONSE");
        }
        BigDecimal sum = probabilities.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        // 동결 실험의 응답 반올림 허용 오차도 유지한다. 확률을 임의로 다시 정규화하지 않는다.
        if (sum.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.03")) > 0
                || probabilities.values().stream()
                        .anyMatch(value -> value.subtract(probabilities.get(choice))
                                .compareTo(new BigDecimal("0.000001")) > 0)) {
            throw new IllegalArgumentException("INVALID_PROBABILITY_DISTRIBUTION");
        }
    }

    /** 기존 실험의 gated_decisions 기준. 모델 직접 검토 선택은 유지한다. */
    public JevDecision evaluate() {
        if ("human_review".equals(choice)) {
            return JevDecision.HUMAN_REVIEW;
        }
        BigDecimal approve = probabilities.get("approve");
        BigDecimal reject = probabilities.get("reject");
        BigDecimal review = probabilities.get("human_review");
        if ("reject".equals(choice)
                && reject.compareTo(new BigDecimal("0.6")) >= 0
                && review.compareTo(new BigDecimal("0.15")) <= 0
                && reject.subtract(approve).compareTo(new BigDecimal("0.3")) >= 0) {
            return JevDecision.REJECT;
        }
        if (approve.compareTo(new BigDecimal("0.6")) >= 0
                && review.compareTo(new BigDecimal("0.3")) <= 0
                && approve.subtract(reject).compareTo(new BigDecimal("0.3")) >= 0) {
            return JevDecision.APPROVE;
        }
        return JevDecision.HUMAN_REVIEW;
    }

    private static boolean isProbability(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) >= 0
                && value.compareTo(BigDecimal.ONE) <= 0;
    }
}
