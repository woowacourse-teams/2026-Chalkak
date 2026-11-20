package com.chalkak.backend.jev.domain;

import java.util.Map;

public record JevEvaluation(
        Map<String, JevChoiceAnswer> answers,
        Map<String, JevDecision> decisions,
        JevDecision recommendation,
        String category,
        Long inputTokens,
        Long outputTokens,
        String error
) {

    public static JevEvaluation review(String error) {
        return new JevEvaluation(
                Map.of(),
                Map.of(),
                JevDecision.HUMAN_REVIEW,
                null,
                null,
                null,
                error
        );
    }
}
