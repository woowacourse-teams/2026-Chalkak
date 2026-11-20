package com.chalkak.backend.post.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PostRegistrationLimitTest {

    @ParameterizedTest
    @CsvSource({"0,10", "9,1", "10,0", "15,0"})
    @DisplayName("하루 총 열 번에서 잔여 횟수를 구하고 과거 초과 기록은 0으로 표시한다")
    void from_registrationCount_returnsRemaining(long usedCount, int remainingCount) {
        // When
        PostRegistrationLimit result = PostRegistrationLimit.from(
                usedCount, Instant.parse("2026-11-19T12:00:00Z"));

        // Then
        assertThat(result.limit()).isEqualTo(10);
        assertThat(result.usedCount()).isEqualTo(usedCount);
        assertThat(result.remainingCount()).isEqualTo(remainingCount);
    }

    @Test
    @DisplayName("아홉 번 등록했으면 열 번째 등록을 허용한다")
    void validateCanRegister_nineRegistrations_allowsTenth() {
        // Given
        PostRegistrationLimit limit = PostRegistrationLimit.from(9, Instant.now());

        // When & Then
        assertThatCode(limit::validateCanRegister).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(longs = {10, 11})
    @DisplayName("열 번 이상 등록했으면 다음 날까지 등록을 차단하는 코드를 반환한다")
    void validateCanRegister_limitReached_rejects(long usedCount) {
        // Given
        PostRegistrationLimit limit = PostRegistrationLimit.from(usedCount, Instant.now());

        // When & Then
        assertThatThrownBy(limit::validateCanRegister)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode",
                        ErrorCode.POST_REGISTRATION_LIMIT_REACHED);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-11-19T14:59:59.999999Z,2026-11-18T15:00:00Z,2026-11-19T15:00:00Z",
            "2026-11-19T15:00:00Z,2026-11-19T15:00:00Z,2026-11-20T15:00:00Z",
            "2026-12-31T15:00:00Z,2026-12-31T15:00:00Z,2027-01-01T15:00:00Z"
    })
    @DisplayName("UTC 날짜가 아닌 KST 자정을 기준으로 일일 범위와 초기화 시각을 계산한다")
    void getDayStart_kstBoundary_returnsHalfOpenWindow(
            Instant now,
            Instant start,
            Instant end
    ) {
        // When & Then
        assertThat(PostRegistrationLimit.getDayStart(now)).isEqualTo(start);
        assertThat(PostRegistrationLimit.getNextDayStart(now)).isEqualTo(end);
        assertThat(PostRegistrationLimit.from(0, now).resetsAt()).isEqualTo(end);
    }
}
