package com.chalkak.backend.post.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PostDescriptionTest {

    @Test
    @DisplayName("null이면 설명 없음으로 보고 null을 돌려준다")
    void from_null_returnsNull() {
        // When
        PostDescription description = PostDescription.from(null);

        // Then
        assertThat(description).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "　　", "\n", " \n\t \r\n "})
    @DisplayName("빈 문자열, 공백뿐, 줄바꿈뿐인 설명은 설명 없음으로 보고 null을 돌려준다")
    void from_blankDescription_returnsNull(String raw) {
        // When
        PostDescription description = PostDescription.from(raw);

        // Then
        assertThat(description).isNull();
    }

    @Test
    @DisplayName("앞뒤 공백과 줄바꿈은 제거하고 가운데 줄바꿈은 유지한다")
    void from_descriptionWithSurroundingWhitespace_stripsOnlySurroundingWhitespace() {
        // When
        PostDescription description = PostDescription.from("  \n첫 줄\n\n\n둘째 줄 \n ");

        // Then
        assertThat(description.value()).isEqualTo("첫 줄\n\n\n둘째 줄");
    }

    @Test
    @DisplayName("이모지를 포함해 code point 기준 300자까지 허용한다")
    void from_threeHundredCodePointDescription_createsPostDescription() {
        // Given
        String raw = "📸".repeat(150) + "\n" + "가".repeat(149);

        // When
        PostDescription description = PostDescription.from(raw);

        // Then
        assertThat(description.value()).isEqualTo(raw);
    }

    @Test
    @DisplayName("code point 기준 300자를 초과하면 거부한다")
    void from_overThreeHundredCodePointDescription_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> PostDescription.from("📸".repeat(301)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("설명은 300자 이하여야 합니다.");
    }

    @Test
    @DisplayName("값 없는 PostDescription은 만들 수 없다")
    void constructor_nullValue_throwsNullPointerException() {
        // When & Then
        assertThatThrownBy(() -> new PostDescription(null))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n"})
    @DisplayName("생성자를 직접 호출할 때 공백뿐인 설명은 거부한다")
    void constructor_blankDescription_throwsBusinessException(String value) {
        // When & Then
        assertThatThrownBy(() -> new PostDescription(value))
                .isInstanceOf(BusinessException.class)
                .hasMessage("설명 없음은 PostDescription이 아니라 null로 표현해야 합니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n", "설명", "a\nb"})
    @DisplayName("null과 공백뿐인 설명과 짧은 설명은 유효하다고 판정한다")
    void isValid_shortOrBlankDescription_returnsTrue(String raw) {
        // When & Then
        assertThat(PostDescription.isValid(raw)).isTrue();
    }

    @Test
    @DisplayName("300자인 설명은 유효하다고 판정한다")
    void isValid_threeHundredCharacters_returnsTrue() {
        // When & Then
        assertThat(PostDescription.isValid("a".repeat(300))).isTrue();
    }

    @Test
    @DisplayName("앞뒤 공백을 제거한 값이 300자면 유효하다고 판정한다")
    void isValid_threeHundredCharactersAfterStrip_returnsTrue() {
        // When & Then
        assertThat(PostDescription.isValid("  \n" + "a".repeat(300) + "\n  ")).isTrue();
    }

    @Test
    @DisplayName("301자인 설명은 유효하지 않다고 판정한다")
    void isValid_overThreeHundredCharacters_returnsFalse() {
        // When & Then
        assertThat(PostDescription.isValid("a".repeat(301))).isFalse();
    }
}
