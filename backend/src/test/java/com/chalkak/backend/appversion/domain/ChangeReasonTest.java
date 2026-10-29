package com.chalkak.backend.appversion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ChangeReasonTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 499, 500})
    @DisplayName("변경 사유는 공백 제거 후 Unicode code point 기준 상한까지 허용한다")
    void create_validLengths_stripsWhitespace(int count) {
        // When & Then
        assertThat(new ChangeReason("  " + "😀".repeat(count) + "  ").value())
                .isEqualTo("😀".repeat(count));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    @DisplayName("변경 사유가 없거나 공백뿐이면 거절한다")
    void create_missingReason_throwsBusinessException(String reason) {
        // When & Then
        assertThatThrownBy(() -> new ChangeReason(reason)).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "😀"})
    @DisplayName("ASCII와 보조 문자의 사유 길이가 상한을 넘으면 거절한다")
    void create_overLimit_throwsBusinessException(String character) {
        // When & Then
        assertThatThrownBy(() -> new ChangeReason(character.repeat(501)))
                .isInstanceOf(BusinessException.class);
    }
}
