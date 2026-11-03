package com.chalkak.backend.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CanonicalUuidParserTest {

    @Test
    @DisplayName("표준 형식의 UUID 문자열을 UUID로 변환한다")
    void parse_canonicalUuid_returnsUuid() {
        // Given
        String value = "0198f6c1-62ba-7d30-8b12-0f733b6570d4";

        // When
        UUID result = CanonicalUuidParser.parse(value);

        // Then
        assertThat(result).isEqualTo(UUID.fromString(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-uuid", "1-1-1-1-1"})
    @DisplayName("잘못되거나 비정규 UUID는 공통 ID 형식 예외로 거절한다")
    void parse_invalidOrNonCanonicalUuid_throwsBusinessException(String value) {
        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> CanonicalUuidParser.parse(value)
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception).hasMessage("ID 형식이 올바르지 않습니다.");
    }

}
