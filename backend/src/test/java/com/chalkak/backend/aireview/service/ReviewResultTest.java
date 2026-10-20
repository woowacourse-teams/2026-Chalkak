package com.chalkak.backend.aireview.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.aireview.domain.AiReview.Status;
import com.chalkak.backend.aireview.service.PostAiReviewService.ApiStatus;
import com.chalkak.backend.aireview.service.PostAiReviewService.ReviewResult;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReviewResultTest {

    @ParameterizedTest
    @CsvSource({
            "QUEUED, PROCESSING",
            "PROCESSING, PROCESSING",
            "COMPLETED, COMPLETED",
            "FAILED, FAILED"
    })
    @DisplayName("내부 대기·처리 상태는 모두 처리 중으로 노출하고 완료·실패는 그대로 노출한다")
    void apiStatus_from_mapsInternalStatus(Status status, ApiStatus expected) {
        // When & Then
        assertThat(ApiStatus.from(status)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "PROCESSING, true",
            "COMPLETED, false",
            "FAILED, false"
    })
    @DisplayName("처리 중 상태일 때만 진행 중이다")
    void isInProgress_byStatus(ApiStatus status, boolean expected) {
        // Given
        ReviewResult result = new ReviewResult(status, Instant.EPOCH, null, null);

        // When & Then
        assertThat(result.isInProgress()).isEqualTo(expected);
    }
}
