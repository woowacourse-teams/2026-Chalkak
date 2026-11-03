package com.chalkak.backend.post.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PostDescriptionTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "　　", "\n", " \n\t \r\n "})
    @DisplayName("null, 빈 문자열, 공백뿐, 줄바꿈뿐인 설명은 설명 없음으로 보고 null을 돌려준다")
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
    void isLengthValid_shortOrBlankDescription_returnsTrue(String raw) {
        // When & Then
        assertThat(PostDescription.isLengthValid(raw)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("descriptionLengthBoundaries")
    @DisplayName("앞뒤 공백을 제거한 code point 기준 300자까지만 유효하다고 판정한다")
    void isLengthValid_lengthBoundary_returnsExpected(String raw, boolean expected) {
        // When & Then
        assertThat(PostDescription.isLengthValid(raw)).isEqualTo(expected);
    }

    private static Stream<Arguments> descriptionLengthBoundaries() {
        return Stream.of(
                Arguments.of("a".repeat(300), true),
                Arguments.of("  \n" + "a".repeat(300) + "\n  ", true),
                Arguments.of("a".repeat(301), false)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"가\u0000나", "\u0000", "  가나\u0000  "})
    @DisplayName("생성자를 직접 호출할 때 NUL 문자가 포함된 설명은 거부한다")
    void constructor_descriptionWithNulCharacter_throwsBusinessException(String value) {
        // When & Then
        assertThatThrownBy(() -> new PostDescription(value))
                .isInstanceOf(BusinessException.class)
                .hasMessage("설명에 사용할 수 없는 문자가 포함되어 있습니다.");
    }

    @Test
    @DisplayName("NUL 문자가 포함된 설명은 설명 없음으로 보지 않고 거부한다")
    void from_descriptionWithNulCharacter_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> PostDescription.from("가\u0000나"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("설명에 사용할 수 없는 문자가 포함되어 있습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "설명", "a\nb", "📸"})
    @DisplayName("null이거나 NUL 문자가 없는 설명은 줄바꿈이 있어도 문자가 유효하다고 판정한다")
    void isCharacterValid_descriptionWithoutNulCharacter_returnsTrue(String raw) {
        // When & Then
        assertThat(PostDescription.isCharacterValid(raw)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"가\u0000나", "\u0000", "  \u0000  "})
    @DisplayName("NUL 문자가 포함된 설명은 문자가 유효하지 않다고 판정한다")
    void isCharacterValid_descriptionWithNulCharacter_returnsFalse(String raw) {
        // When & Then
        assertThat(PostDescription.isCharacterValid(raw)).isFalse();
    }
}
