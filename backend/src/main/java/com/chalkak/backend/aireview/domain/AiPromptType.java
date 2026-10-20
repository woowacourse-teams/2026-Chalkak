package com.chalkak.backend.aireview.domain;

import java.util.Set;

/**
 * AI 프롬프트의 용도. 용도마다 템플릿에서 쓸 수 있는 변수와 모델이 지켜야 할 출력 스키마 버전이 정해진다.
 */
public enum AiPromptType {

    PHOTO_REVIEW("topicTitle", String.valueOf(PhotoReviewOutput.schemaVersion()));

    private final String topicTitleVariable;
    private final String outputSchemaVersion;

    AiPromptType(String topicTitleVariable, String outputSchemaVersion) {
        this.topicTitleVariable = topicTitleVariable;
        this.outputSchemaVersion = outputSchemaVersion;
    }

    String topicTitleVariable() {
        return topicTitleVariable;
    }

    Set<String> allowedVariables() {
        return Set.of(topicTitleVariable);
    }

    public String outputSchemaVersion() {
        return outputSchemaVersion;
    }
}
