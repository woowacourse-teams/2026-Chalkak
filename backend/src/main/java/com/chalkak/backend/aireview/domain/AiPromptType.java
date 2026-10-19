package com.chalkak.backend.aireview.domain;

import java.util.Set;

/**
 * AI 프롬프트의 용도. 용도마다 템플릿에서 쓸 수 있는 변수가 정해진다.
 */
public enum AiPromptType {

    PHOTO_REVIEW("topicTitle");

    private final String topicTitleVariable;

    AiPromptType(String topicTitleVariable) {
        this.topicTitleVariable = topicTitleVariable;
    }

    String topicTitleVariable() {
        return topicTitleVariable;
    }

    Set<String> allowedVariables() {
        return Set.of(topicTitleVariable);
    }
}
