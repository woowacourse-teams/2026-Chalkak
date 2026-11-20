package com.chalkak.backend.jev.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JevPromptVersionTest {

    @Test
    @DisplayName("저장할 새 Jev 버전은 비활성 상태이며 입력한 규칙만 보관한다")
    void create_validInput_startsInactive() {
        // Given
        JevPromptConfiguration configuration = JevPromptFixtures.photo();

        // When
        JevPromptVersion version = JevPromptVersion.create(
                JevPromptRole.JEV_PHOTO_MODERATION,
                "jev-1.14.0",
                configuration,
                1,
                " 입력 메모 ",
                UUID.randomUUID()
        );

        // Then
        assertThat(version.isActive()).isFalse();
        assertThat(version.getActivatedAt()).isNull();
        assertThat(version.getConfiguration()).isEqualTo(configuration);
        assertThat(version.getMemo()).isEqualTo("입력 메모");
    }

    @ParameterizedTest
    @ValueSource(strings = {"gpt-6.1-sol", "jev-preview", "jev-latest", "jev-1.13.0"})
    @DisplayName("GPT 모델·가변 별칭·미검증 Jev 버전은 거절한다")
    void create_unapprovedModel_rejects(String model) {
        // When & Then
        assertThatThrownBy(() -> JevPromptVersion.create(
                JevPromptRole.JEV_TOPIC_CLASSIFICATION,
                model,
                JevPromptFixtures.classification(),
                1,
                null,
                UUID.randomUUID()
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("분류 질문을 사진 검수 프롬프트로 저장하지 못한다")
    void create_wrongRoleConfiguration_rejects() {
        // When & Then
        assertThatThrownBy(() -> JevPromptVersion.create(
                JevPromptRole.JEV_PHOTO_MODERATION,
                "jev-1.14.0",
                JevPromptFixtures.classification(),
                1,
                null,
                UUID.randomUUID()
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("사진 세 질문의 누락과 기본 카테고리 규칙 누락을 거절한다")
    void validateRole_missingQuestionOrGeneralRule_rejects() {
        // Given
        JevPromptConfiguration photo = JevPromptFixtures.photo();
        JevPromptConfiguration missingRule = new JevPromptConfiguration(
                photo.questions(),
                Map.of(),
                Map.of()
        );
        JevPromptConfiguration missingQuestion = new JevPromptConfiguration(
                Map.of("topic_fit", photo.questions().get("topic_fit")),
                photo.categoryRules(),
                Map.of()
        );

        // When & Then
        assertThatThrownBy(() -> missingRule.validateRole(JevPromptRole.JEV_PHOTO_MODERATION))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> missingQuestion.validateRole(JevPromptRole.JEV_PHOTO_MODERATION))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("빈 지침이나 선택지 설명을 가진 Choice를 거절한다")
    void createQuestion_emptyInstructionsOrCriteria_rejects() {
        // When & Then
        assertThatThrownBy(() -> new JevChoiceQuestion(
                "choice",
                " ",
                Map.of(
                        "a",
                        "허용",
                        "b",
                        "거절"
                )
        ))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new JevChoiceQuestion(
                "choice",
                "지침",
                Map.of(
                        "a",
                        " ",
                        "b",
                        "거절"
                )
        ))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("선택지 순서를 유지하고 외부 Map 수정으로 프롬프트가 바뀌지 않는다")
    void createQuestion_mutableInput_preservesOrderAndContent() {
        // Given
        Map<String, String> criteria = new LinkedHashMap<>();
        criteria.put("reject", "반려");
        criteria.put("approve", "승인");

        // When
        JevChoiceQuestion question = new JevChoiceQuestion(
                "choice",
                "지침",
                criteria
        );
        criteria.put("other", "기타");

        // Then
        assertThat(question.criteria().keySet()).containsExactly("reject", "approve");
        assertThatThrownBy(() -> question.criteria().put("other", "기타"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 256})
    @DisplayName("공급자가 허용하는 선택지 개수 범위를 벗어나면 거절한다")
    void createQuestion_invalidOptionCount_rejects(int count) {
        // Given
        Map<String, String> criteria = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            criteria.put("option_" + index, "선택지 설명");
        }

        // When & Then
        assertThatThrownBy(() -> new JevChoiceQuestion(
                "choice",
                "지침",
                criteria
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("분류 질문은 다른 의미를 처리할 general 선택지가 있어야 한다")
    void validateRole_classificationMissingGeneral_rejects() {
        // Given
        JevPromptConfiguration configuration = new JevPromptConfiguration(
                Map.of("topic_category", new JevChoiceQuestion(
                        "choice",
                        "지침",
                        Map.of(
                                "subject",
                                "사물",
                                "visual",
                                "시각"
                        )
                )),
                Map.of(),
                Map.of()
        );

        // When & Then
        assertThatThrownBy(() -> configuration.validateRole(JevPromptRole.JEV_TOPIC_CLASSIFICATION))
                .isInstanceOf(BusinessException.class);
    }
}
