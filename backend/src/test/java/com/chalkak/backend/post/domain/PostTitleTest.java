package com.chalkak.backend.post.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PostTitleTest {

    @Test
    @DisplayName("제목을 그대로 보관한다")
    void from_validTitle_createsPostTitle() {
        // When
        PostTitle title = PostTitle.from("오늘의 기록");

        // Then
        assertThat(title).isNotNull();
        assertThat(title.value()).isEqualTo("오늘의 기록");
    }

    @Test
    @DisplayName("앞뒤 공백은 제거하고 가운데 공백은 유지한다")
    void from_titleWithSurroundingSpaces_stripsOnlySurroundingSpaces() {
        // When
        PostTitle title = PostTitle.from("  오늘의  기록  ");

        // Then
        assertThat(title.value()).isEqualTo("오늘의  기록");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "　　"})
    @DisplayName("null, 빈 문자열, 공백뿐인 제목은 제목 없음으로 보고 null을 돌려준다")
    void from_blankTitle_returnsNull(String raw) {
        // When
        PostTitle title = PostTitle.from(raw);

        // Then
        assertThat(title).isNull();
    }

    @Test
    @DisplayName("앞뒤 공백을 제거한 값이 10자면 허용한다")
    void from_tenCharacterNormalizedTitle_createsPostTitle() {
        // When
        PostTitle title = PostTitle.from("  1234567890  ");

        // Then
        assertThat(title.value()).isEqualTo("1234567890");
    }

    @Test
    @DisplayName("이모지 제목은 code point 기준으로 10자까지 허용한다")
    void from_tenCodePointEmojiTitle_createsPostTitle() {
        // Given
        String raw = "📸".repeat(10);

        // When
        PostTitle title = PostTitle.from(raw);

        // Then
        assertThat(title.value()).isEqualTo(raw);
    }

    @Test
    @DisplayName("앞뒤 공백을 제거한 값이 code point 기준 10자를 초과하면 거부한다")
    void from_tooLongNormalizedTitle_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> PostTitle.from("  " + "📸".repeat(11) + "  "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("제목은 10자 이하여야 합니다.");
    }

    @Test
    @DisplayName("값 없는 PostTitle은 만들 수 없다")
    void constructor_nullValue_throwsNullPointerException() {
        // When & Then
        assertThatThrownBy(() -> new PostTitle(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("생성자를 직접 호출해도 앞뒤 공백을 제거한다")
    void constructor_titleWithSurroundingSpaces_stripsTitle() {
        // When
        PostTitle title = new PostTitle("  오늘의 기록  ");

        // Then
        assertThat(title.value()).isEqualTo("오늘의 기록");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "　　"})
    @DisplayName("생성자를 직접 호출할 때 공백뿐인 제목은 거부한다")
    void constructor_blankTitle_throwsBusinessException(String value) {
        // When & Then
        assertThatThrownBy(() -> new PostTitle(value))
                .isInstanceOf(BusinessException.class)
                .hasMessage("제목 없음은 PostTitle이 아니라 null로 표현해야 합니다.");
    }

    @Test
    @DisplayName("생성자를 직접 호출할 때 10자를 초과하는 제목은 거부한다")
    void constructor_tooLongTitle_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> new PostTitle("12345678901"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("제목은 10자 이하여야 합니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "오늘의 기록", "1234567890", "  1234567890  "})
    @DisplayName("공백 제거 후 10자 이하인 제목은 유효하다고 판정한다")
    void isValid_titleWithinMaxLength_returnsTrue(String raw) {
        // When & Then
        assertThat(PostTitle.isValid(raw)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678901", "  12345678901  "})
    @DisplayName("공백 제거 후 10자를 초과하는 제목은 유효하지 않다고 판정한다")
    void isValid_titleExceedingMaxLength_returnsFalse(String raw) {
        // When & Then
        assertThat(PostTitle.isValid(raw)).isFalse();
    }
}
