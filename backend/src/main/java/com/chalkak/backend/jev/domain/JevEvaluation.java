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
}
