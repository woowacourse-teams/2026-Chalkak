package com.chalkak.backend.aireview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AiPromptDraftTest {

    private static final AiPromptParameters PARAMETERS = new AiPromptParameters(
            null,
            500,
            null,
            null
    );

    @Test
    @DisplayName("생성_허용된 topicTitle 변수만 쓰면_성공한다")
    void create_allowedVariableOnly_succeeds() {
        // When & Then
        assertThatCode(() -> draft("사진을 평가한다.", "주제는 {{topicTitle}}입니다."))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("생성_허용되지 않은 변수를 쓰면_거부한다")
    void create_unknownVariable_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> draft("사진을 평가한다.", "작성자는 {{userId}}입니다."))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("생성_변수 없는 템플릿이면_성공한다")
    void create_templateWithoutVariable_succeeds() {
        // When & Then
        assertThatCode(() -> draft("사진을 평가한다.", "이 사진을 평가해 주세요."))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("렌더링_같은 변수가 두 번 나오면_모두 치환한다")
    void renderUserPrompt_repeatedVariable_replacesEveryOccurrence() {
        // Given
        AiPromptDraft draft = draft("사진을 평가한다.", "{{topicTitle}}: {{topicTitle}}를 담은 사진");

        // When
        String rendered = draft.renderUserPrompt("노을");

        // Then
        assertThat(rendered).isEqualTo("노을: 노을를 담은 사진");
    }

    @Test
    @DisplayName("렌더링_JSON 문자열에 쓰는 값은_따옴표 역슬래시 개행을 이스케이프한다")
    void renderUserPrompt_specialCharacters_areJsonEscaped() {
        // Given
        AiPromptDraft draft = draft("사진을 평가한다.", "{\"주제\": \"{{topicTitle}}\"}");

        // When
        String rendered = draft.renderUserPrompt("a\"b\\c\nd\te");

        // Then
        assertThat(rendered).isEqualTo("{\"주제\": \"a\\\"b\\\\c\\nd\\te\"}");
    }

    @Test
    @DisplayName("렌더링_일반 한글 값은_그대로 넣는다")
    void renderUserPrompt_ordinaryKorean_isUnchanged() {
        // Given
        AiPromptDraft draft = draft("사진을 평가한다.", "{\"주제\": \"{{topicTitle}}\"}");

        // When & Then
        assertThat(draft.renderUserPrompt("노을 지는 바다 :)"))
                .isEqualTo("{\"주제\": \"노을 지는 바다 :)\"}");
    }

    @Test
    @DisplayName("렌더링_값에 변수 표기가 있어도_다시 치환하지 않는다")
    void renderUserPrompt_valueContainsPlaceholder_doesNotExpandAgain() {
        // Given
        AiPromptDraft draft = draft("사진을 평가한다.", "주제: {{topicTitle}}");

        // When
        String rendered = draft.renderUserPrompt("{{topicTitle}} $1");

        // Then
        assertThat(rendered).isEqualTo("주제: {{topicTitle}} $1");
    }

    @Test
    @DisplayName("렌더링_주제 제목이 null이면_거부한다")
    void renderUserPrompt_nullTopicTitle_throwsIllegalArgumentException() {
        // Given
        AiPromptDraft draft = draft("사진을 평가한다.", "주제: {{topicTitle}}");

        // When & Then
        assertThatThrownBy(() -> draft.renderUserPrompt(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    @DisplayName("생성_systemPrompt가 비어 있으면_거부한다")
    void create_blankSystemPrompt_throwsBusinessException(String systemPrompt) {
        // When & Then
        assertThatThrownBy(() -> draft(systemPrompt, "주제: {{topicTitle}}"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("생성_systemPrompt에 변수 표기가 있으면_거부한다")
    void create_placeholderInSystemPrompt_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> draft("주제는 {{topicTitle}}", "주제: {{topicTitle}}"))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    @DisplayName("생성_userPromptTemplate이 비어 있으면_거부한다")
    void create_blankTemplate_throwsBusinessException(String template) {
        // When & Then
        assertThatThrownBy(() -> draft("사진을 평가한다.", template))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("생성_model이 비어 있으면_거부한다")
    void create_blankModel_throwsBusinessException(String model) {
        // When & Then
        assertThatThrownBy(() -> new AiPromptDraft(
                AiPromptType.PHOTO_REVIEW,
                "사진을 평가한다.",
                "주제: {{topicTitle}}",
                model,
                PARAMETERS
        ))
                .isInstanceOf(BusinessException.class);
    }

    private AiPromptDraft draft(String systemPrompt, String template) {
        return new AiPromptDraft(
                AiPromptType.PHOTO_REVIEW,
                systemPrompt,
                template,
                "gpt-5-mini",
                PARAMETERS
        );
    }
}
