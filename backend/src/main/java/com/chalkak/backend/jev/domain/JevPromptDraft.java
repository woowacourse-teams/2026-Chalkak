package com.chalkak.backend.jev.domain;

import java.util.UUID;

/** 시험 실행에서만 보존하는 미저장 초안. 운영 작업에는 불변 프롬프트 버전 ID를 사용한다. */
public record JevPromptDraft(
        String model,
        JevPromptConfiguration classification,
        JevPromptConfiguration photo
) {

    public void validate(UUID adminId) {
        JevPromptVersion.create(
                JevPromptRole.JEV_TOPIC_CLASSIFICATION,
                model,
                classification,
                1,
                null,
                adminId
        );
        JevPromptVersion.create(
                JevPromptRole.JEV_PHOTO_MODERATION,
                model,
                photo,
                1,
                null,
                adminId
        );
        if (!photo.categoryRules().keySet().containsAll(
                classification.questions().get("topic_category").criteria().keySet())) {
            throw new IllegalArgumentException("INCOMPATIBLE_CATEGORY_RULES");
        }
    }
}
