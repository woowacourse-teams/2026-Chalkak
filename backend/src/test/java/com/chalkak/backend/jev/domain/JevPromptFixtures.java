package com.chalkak.backend.jev.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/** 테스트 입력이며 운영 초기 프롬프트로 자동 등록하지 않는다. */
public final class JevPromptFixtures {

    private JevPromptFixtures() {
    }

    public static JevPromptConfiguration classification() {
        return new JevPromptConfiguration(
                Map.of("topic_category", new JevChoiceQuestion(
                        "choice",
                        "테스트 주제를 분류한다.",
                        Map.of(
                                "subject",
                                "사물",
                                "general",
                                "기타"
                        )
                )),
                Map.of(),
                Map.of()
        );
    }

    public static JevPromptConfiguration photo() {
        Map<String, JevChoiceQuestion> questions = new LinkedHashMap<>();
        for (String name : new String[]{"topic_fit", "image_safety", "image_expression"}) {
            questions.put(name, new JevChoiceQuestion(
                    "choice",
                    "테스트 사진 검사: " + name,
                    Map.of(
                            "approve",
                            "허용",
                            "human_review",
                            "검토",
                            "reject",
                            "위반"
                    )
            ));
        }
        return new JevPromptConfiguration(
                questions,
                Map.of(
                        "general",
                        "테스트 기본 규칙",
                        "subject",
                        "테스트 사물 규칙"
                ),
                Map.of()
        );
    }
}
