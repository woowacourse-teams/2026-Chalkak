package com.chalkak.backend.jev.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 관리자 정답은 실제 공개 상태 및 모델 원응답과 독립적이다. */
public record JevGoldLabel(
        JevDecision decision,
        Map<String, JevDecision> questions,
        String reason,
        Sampling sampling,
        UUID evaluatedBy,
        Instant evaluatedAt
) {

    private static final Set<String> QUESTIONS = Set.of(
            "topic_fit",
            "image_safety",
            "image_expression"
    );

    public JevGoldLabel {
        if (decision == null || questions == null || sampling == null || evaluatedBy == null
                || evaluatedAt == null || reason == null || reason.isBlank()
                || reason.strip().codePointCount(0, reason.strip().length()) > 500
                || !QUESTIONS.containsAll(questions.keySet())
                || questions.values().stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("평가 정답이 올바르지 않습니다.");
        }
        questions = Map.copyOf(questions);
        reason = reason.strip();
    }

    public enum Sampling {
        RANDOM, ERROR_ANALYSIS, UNSPECIFIED
    }
}
