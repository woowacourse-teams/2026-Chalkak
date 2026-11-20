package com.chalkak.backend.jev.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 프롬프트 원문은 관리자 입력으로만 생성한다. 카테고리별 지침도 같은 버전에 보관한다. */
public record JevPromptConfiguration(
        Map<String, JevChoiceQuestion> questions,
        Map<String, String> categoryRules,
        Map<String, String> categoryInstructions
) {

    private static final Set<String> PHOTO_QUESTIONS = Set.of(
            "topic_fit",
            "image_safety",
            "image_expression"
    );
    private static final Set<String> DECISIONS = Set.of(
            "approve",
            "human_review",
            "reject"
    );

    public JevPromptConfiguration {
        if (questions == null || questions.isEmpty()
                || questions.values().stream().anyMatch(value -> value == null)) {
            throw invalidConfiguration();
        }
        questions = Collections.unmodifiableMap(new LinkedHashMap<>(questions));
        categoryRules = copyInstructions(categoryRules);
        categoryInstructions = copyInstructions(categoryInstructions);
    }

    public void validateRole(JevPromptRole role) {
        if (role == JevPromptRole.JEV_TOPIC_CLASSIFICATION) {
            if (!questions.keySet().equals(Set.of("topic_category"))
                    || !questions.get("topic_category").criteria().containsKey("general")
                    || !categoryRules.isEmpty() || !categoryInstructions.isEmpty()) {
                throw invalidConfiguration();
            }
            return;
        }
        if (role != JevPromptRole.JEV_PHOTO_MODERATION
                || !questions.keySet().equals(PHOTO_QUESTIONS)
                || questions.values().stream()
                        .anyMatch(question -> !question.criteria().keySet().equals(DECISIONS))
                || !categoryRules.containsKey("general")
                || !categoryRules.keySet().containsAll(categoryInstructions.keySet())) {
            throw invalidConfiguration();
        }
    }

    private static Map<String, String> copyInstructions(Map<String, String> source) {
        if (source == null) {
            return Map.of();
        }
        source.forEach((name, instruction) -> {
            if (name == null || !name.matches("[a-z][a-z0-9_]{0,63}")
                    || instruction == null || instruction.isBlank()) {
                throw invalidConfiguration();
            }
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static BusinessException invalidConfiguration() {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, "Jev 용도별 질문과 카테고리 규칙이 올바르지 않습니다.");
    }
}
