package com.chalkak.backend.like.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LikeMilestonePolicyTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, 0",
            "4, 0",
            "5, 5",
            "6, 5",
            "9, 5",
            "10, 10",
            "11, 10",
            "18, 15",
            "9223372036854775807, 9223372036854775805"
    })
    @DisplayName("현재 좋아요 수 이하의 최고 5개 단계를 계산하고 5개 미만이면 0을 반환한다")
    void calculateCandidate_likeCount_returnsHighestReachedMilestone(long likeCount,
            long expected) {
        // When
        long candidate = LikeMilestonePolicy.calculateCandidate(likeCount);

        // Then
        assertThat(candidate).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(longs = {5L, 10L, 15L, 9223372036854775805L})
    @DisplayName("5 이상의 5의 배수는 유효한 단계로 허용한다")
    void validate_validMilestone_acceptsCount(long milestoneCount) {
        // When & Then
        assertThatCode(() -> LikeMilestonePolicy.validate(milestoneCount))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(longs = {-5L, 0L, 4L, 6L, 9L, 11L, Long.MAX_VALUE})
    @DisplayName("5 미만이거나 5의 배수가 아닌 단계는 기존 비즈니스 예외로 거부한다")
    void validate_invalidMilestone_throwsBusinessException(long milestoneCount) {
        // When & Then
        assertThatThrownBy(() -> LikeMilestonePolicy.validate(milestoneCount))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
                    assertThat(exception.getMessage()).isEqualTo("좋아요 단계는 5 이상의 5단위여야 합니다.");
                });
    }
}
