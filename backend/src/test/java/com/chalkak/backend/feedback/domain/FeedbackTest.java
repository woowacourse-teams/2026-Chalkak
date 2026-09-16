package com.chalkak.backend.feedback.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FeedbackTest {

    private static final UUID USER_ID =
            UUID.fromString("0198fd00-0000-7000-8000-000000000001");

    @Test
    @DisplayName("피드백 내용의 앞뒤 공백을 제거해 보관한다")
    void create_contentWithSurroundingWhitespace_storesStrippedContent() {
        Feedback feedback = Feedback.create(USER_ID, "  사진 업로드가 느려요.\n");

        assertThat(feedback.getContent()).isEqualTo("사진 업로드가 느려요.");
        assertThat(feedback.getUserId()).isEqualTo(USER_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t"})
    @DisplayName("내용이 비었거나 공백뿐이면 거절한다")
    void create_blankContent_throwsBusinessException(String content) {
        assertThatThrownBy(() -> Feedback.create(USER_ID, content))
                .isInstanceOf(BusinessException.class)
                .hasMessage("피드백 내용이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("내용이 null이면 거절한다")
    void create_nullContent_throwsBusinessException() {
        assertThatThrownBy(() -> Feedback.create(USER_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("피드백 내용이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("앞뒤 공백을 제거한 1000자는 허용한다")
    void create_contentAtMaxLength_storesContent() {
        String content = "가".repeat(Feedback.MAX_CONTENT_LENGTH);

        Feedback feedback = Feedback.create(USER_ID, "  " + content + "  ");

        assertThat(feedback.getContent()).isEqualTo(content);
    }

    @Test
    @DisplayName("앞뒤 공백을 제거한 1001자는 거절한다")
    void create_contentOverMaxLength_throwsBusinessException() {
        String content = "가".repeat(Feedback.MAX_CONTENT_LENGTH + 1);

        assertThatThrownBy(() -> Feedback.create(USER_ID, content))
                .isInstanceOf(BusinessException.class)
                .hasMessage("피드백 내용이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("보조 평면 문자는 code point 하나로 세어 1000자까지 허용한다")
    void create_supplementaryCharactersAtMaxLength_storesContent() {
        String content = "😀".repeat(Feedback.MAX_CONTENT_LENGTH);

        Feedback feedback = Feedback.create(USER_ID, content);

        assertThat(feedback.getContent()).isEqualTo(content);
    }
}
