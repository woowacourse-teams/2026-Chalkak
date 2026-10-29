package com.chalkak.backend.appversion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MinimumSupportedVersionTest {

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0", "1.10.0", "2147483646.2147483646.2147483646",
            "2147483647.2147483647.2147483647"})
    @DisplayName("각 자리의 0부터 정수 상한까지 정규 형식 버전을 허용한다")
    void create_validBoundaries_preservesVersion(String value) {
        // When & Then
        assertThat(new MinimumSupportedVersion(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "1.2", "1.2.3.4", "01.2.3", "1.02.3", "1.2.03", "-1.0.0", "v1.2.3",
            "1.2.3-beta", "1.2.3+4", "1.2.3 ", "2147483648.0.0", "0.2147483648.0", "0.0.2147483648",
            "11111111111.0.0"})
    @DisplayName("형식·앞자리 0·각 자리 정수 상한을 어기면 거절한다")
    void create_invalidVersion_throwsBusinessException(String value) {
        // When & Then
        assertThatThrownBy(() -> new MinimumSupportedVersion(value))
                .isInstanceOf(BusinessException.class);
    }
}
