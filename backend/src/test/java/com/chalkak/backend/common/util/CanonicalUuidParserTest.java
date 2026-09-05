package com.chalkak.backend.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    @Test
    @DisplayName("UUID가 아닌 문자열이면 공통 ID 형식 예외를 발생시킨다")
    void parse_invalidUuid_throwsBusinessException() {
        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> CanonicalUuidParser.parse("invalid-uuid")
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception).hasMessage("ID 형식이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("표준 형식이 아닌 UUID 문자열이면 공통 ID 형식 예외를 발생시킨다")
    void parse_nonCanonicalUuid_throwsBusinessException() {
        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> CanonicalUuidParser.parse("1-1-1-1-1")
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception).hasMessage("ID 형식이 올바르지 않습니다.");
    }
}
