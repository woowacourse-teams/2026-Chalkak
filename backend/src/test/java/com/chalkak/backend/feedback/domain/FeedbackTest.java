package com.chalkak.backend.feedback.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.util.UUID;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class FeedbackTest {

    private static final UUID USER_ID = UUID.fromString("0198fd00-0000-7000-8000-000000000001");

    private static final Instant CREATED_AT = Instant.parse("2026-11-03T06:00:00Z");

    @Test
    @DisplayName("같은 마이크로초에 재답변해도 이전 답변과 구분되는 시각을 저장한다")
    void answer_deletedAnswerAtSameTime_advancesVersion() {
        // Given
        Feedback feedback = Feedback.create(
                USER_ID,
                "문의 원문",
                CREATED_AT
        );
        feedback.answer("이전 답변", CREATED_AT);
        feedback.deleteAnswer(CREATED_AT, CREATED_AT);

        // When
        feedback.answer("새 답변", CREATED_AT);

        // Then
        assertThat(feedback.getAnswerContent()).isEqualTo("새 답변");
        assertThat(feedback.getAnswerDeletedAt()).isNull();
        assertThat(feedback.getAnsweredAt()).isEqualTo(CREATED_AT.plus(1, ChronoUnit.MICROS));
        assertThatThrownBy(() -> feedback.deleteAnswer(CREATED_AT, CREATED_AT))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("답변은 공백 제거 후 3000 코드 포인트까지 허용한다")
    void answer_maxLengthAfterStrip_storesAnswer() {
        // Given
        Feedback feedback = Feedback.create(
                USER_ID,
                "문의",
                CREATED_AT
        );
        String content = "😀".repeat(3000);

        // When
        feedback.answer("  " + content + "  ", CREATED_AT);

        // Then
        assertThat(feedback.getAnswerContent()).isEqualTo(content);
        assertThat(feedback.getAnsweredAt()).isEqualTo(CREATED_AT);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    @DisplayName("비어 있거나 공백뿐인 답변을 거절한다")
    void answer_blankContent_rejects(String content) {
        Feedback feedback = Feedback.create(
                USER_ID,
                "문의",
                CREATED_AT
        );

        assertThatThrownBy(() -> feedback.answer(content, CREATED_AT))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("3000 코드 포인트를 초과하는 답변을 거절한다")
    void answer_contentOverMaxLength_rejects() {
        Feedback feedback = Feedback.create(
                USER_ID,
                "문의",
                CREATED_AT
        );

        assertThatThrownBy(() -> feedback.answer("😀".repeat(3001), CREATED_AT))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("삭제된 문의에는 답변할 수 없다")
    void answer_deletedFeedback_rejects() {
        Feedback feedback = Feedback.create(
                USER_ID,
                "문의",
                CREATED_AT
        );
        feedback.delete(CREATED_AT);

        assertThatThrownBy(() -> feedback.answer("답변", CREATED_AT))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("중복 답변은 최초 답변을 덮어쓰지 않는다")
    void answer_alreadyAnswered_preservesOriginal() {
        Feedback feedback = Feedback.create(
                USER_ID,
                "문의",
                CREATED_AT
        );
        feedback.answer("최초 답변", CREATED_AT);

        assertThatThrownBy(() -> feedback.answer("중복 답변", CREATED_AT.plusSeconds(1)))
                .isInstanceOf(BusinessException.class);
        assertThat(feedback.getAnswerContent()).isEqualTo("최초 답변");
        assertThat(feedback.getAnsweredAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("문의 삭제는 원문·작성자를 보존하고 삭제 시각만 기록한다")
    void delete_activeFeedback_preservesContentAndAuthor() {
        // Given
        Feedback feedback = Feedback.create(
                USER_ID,
                "문의 원문",
                CREATED_AT
        );
        Instant deletedAt = Instant.parse("2026-11-03T07:00:00Z");

        // When
        feedback.delete(deletedAt);

        // Then
        assertThat(feedback.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(feedback.getContent()).isEqualTo("문의 원문");
        assertThat(feedback.getUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("이미 삭제한 문의는 다시 삭제해도 최초 삭제 시각을 유지한다")
    void delete_alreadyDeletedFeedback_keepsOriginalTime() {
        // Given
        Feedback feedback = Feedback.create(
                USER_ID,
                "문의 원문",
                CREATED_AT
        );
        Instant firstDeletedAt = Instant.parse("2026-11-03T07:00:00.123456789Z");
        feedback.delete(firstDeletedAt);

        // When
        feedback.delete(firstDeletedAt.plusSeconds(60));

        // Then
        assertThat(feedback.getDeletedAt())
                .isEqualTo(Instant.parse("2026-11-03T07:00:00.123456Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-03T07:00:00.123456789Z", "2026-11-03T07:00:00.123456001Z"})
    @DisplayName("삭제 시각은 반올림하지 않고 DB의 마이크로초 정밀도에 맞춰 버린다")
    void delete_subMicrosecondTime_truncatesToDatabasePrecision(String deletedAt) {
        // Given
        Feedback feedback = Feedback.create(
                USER_ID,
                "문의 원문",
                CREATED_AT
        );

        // When
        feedback.delete(Instant.parse(deletedAt));

        // Then
        assertThat(feedback.getDeletedAt())
                .isEqualTo(Instant.parse("2026-11-03T07:00:00.123456Z"));
    }

    @Test
    @DisplayName("피드백 내용의 앞뒤 공백을 제거해 보관한다")
    void create_contentWithSurroundingWhitespace_storesStrippedContent() {
        Feedback feedback = Feedback.create(
                USER_ID,
                "  사진 업로드가 느려요.\n",
                CREATED_AT
        );

        assertThat(feedback.getContent()).isEqualTo("사진 업로드가 느려요.");
        assertThat(feedback.getUserId()).isEqualTo(USER_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t"})
    @DisplayName("내용이 비었거나 공백뿐이면 거절한다")
    void create_blankContent_throwsBusinessException(String content) {
        assertThatThrownBy(() -> Feedback.create(
                USER_ID,
                content,
                CREATED_AT
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("피드백 내용이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("내용이 null이면 거절한다")
    void create_nullContent_throwsBusinessException() {
        assertThatThrownBy(() -> Feedback.create(
                USER_ID,
                null,
                CREATED_AT
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("피드백 내용이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("앞뒤 공백을 제거한 1000자는 허용한다")
    void create_contentAtMaxLength_storesContent() {
        String content = "가".repeat(Feedback.MAX_CONTENT_LENGTH);

        Feedback feedback = Feedback.create(
                USER_ID,
                "  " + content + "  ",
                CREATED_AT
        );

        assertThat(feedback.getContent()).isEqualTo(content);
    }

    @Test
    @DisplayName("앞뒤 공백을 제거한 1001자는 거절한다")
    void create_contentOverMaxLength_throwsBusinessException() {
        String content = "가".repeat(Feedback.MAX_CONTENT_LENGTH + 1);

        assertThatThrownBy(() -> Feedback.create(
                USER_ID,
                content,
                CREATED_AT
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("피드백 내용이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("보조 평면 문자는 code point 하나로 세어 1000자까지 허용한다")
    void create_supplementaryCharactersAtMaxLength_storesContent() {
        String content = "😀".repeat(Feedback.MAX_CONTENT_LENGTH);

        Feedback feedback = Feedback.create(
                USER_ID,
                content,
                CREATED_AT
        );

        assertThat(feedback.getContent()).isEqualTo(content);
    }
}
