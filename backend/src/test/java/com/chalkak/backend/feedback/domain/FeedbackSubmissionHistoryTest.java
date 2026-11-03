package com.chalkak.backend.feedback.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FeedbackSubmissionHistoryTest {

    private static final Instant NOW = Instant.parse("2026-11-03T06:00:00Z");

    @Test
    @DisplayName("첫 문의는 접수할 수 있다")
    void validateSubmissionAt_noHistory_allowsSubmission() {
        // When & Then
        assertThatCode(() -> new FeedbackSubmissionHistory(List.of()).validateSubmissionAt(NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("1분 직전에는 다음 접수 가능 시각과 함께 거절한다")
    void validateSubmissionAt_beforeMinuteBoundary_rejectsWithNextTime() {
        // Given
        Instant previous = NOW.minusSeconds(60).plusNanos(1);

        // When & Then
        assertThatThrownBy(
                () -> new FeedbackSubmissionHistory(List.of(previous)).validateSubmissionAt(NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(NOW.plusNanos(1).toString());
    }

    @ParameterizedTest
    @ValueSource(longs = {60, 61})
    @DisplayName("직전 문의에서 정확히 1분이 지났거나 더 지났으면 접수한다")
    void validateSubmissionAt_atOrAfterMinuteBoundary_allowsSubmission(long secondsAgo) {
        // When & Then
        assertThatCode(() -> new FeedbackSubmissionHistory(List.of(NOW.minusSeconds(secondsAgo)))
                .validateSubmissionAt(NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("24시간 내 다섯 건이면 다섯 번째 최근 접수의 만료 시각까지 거절한다")
    void validateSubmissionAt_fiveRecentSubmissions_rejectsUntilDailyBoundary() {
        // Given
        List<Instant> times = dailyHistory(NOW.minusSeconds(86400).plusNanos(1));

        // When & Then
        assertThatThrownBy(() -> new FeedbackSubmissionHistory(times).validateSubmissionAt(NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(NOW.plusNanos(1).toString());
    }

    @ParameterizedTest
    @ValueSource(longs = {86400, 86401})
    @DisplayName("다섯 번째 최근 문의가 정확히 24시간이 됐거나 지났으면 새로 접수한다")
    void validateSubmissionAt_atOrAfterDailyBoundary_allowsSubmission(long secondsAgo) {
        // When & Then
        assertThatCode(
                () -> new FeedbackSubmissionHistory(dailyHistory(NOW.minusSeconds(secondsAgo)))
                        .validateSubmissionAt(NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("24시간 제한보다 1분 제한이 늦게 풀리면 더 늦은 시각을 안내한다")
    void validateSubmissionAt_overlappingLimits_reportsLaterMinuteBoundary() {
        // Given
        List<Instant> times = List.of(
                NOW.minusSeconds(86390),
                NOW.minusSeconds(180),
                NOW.minusSeconds(120),
                NOW.minusSeconds(60),
                NOW.minusSeconds(30)
        );

        // When & Then
        assertThatThrownBy(() -> new FeedbackSubmissionHistory(times).validateSubmissionAt(NOW))
                .hasMessageContaining(NOW.plusSeconds(30).toString());
    }

    @Test
    @DisplayName("기존 이력이 다섯 건을 넘어도 가장 오래된 한 건만 만료됐다고 허용하지 않는다")
    void validateSubmissionAt_moreThanFiveSubmissions_usesFifthLatestTime() {
        // Given
        List<Instant> times = List.of(
                NOW.minusSeconds(86400),
                NOW.minusSeconds(300),
                NOW.minusSeconds(240),
                NOW.minusSeconds(180),
                NOW.minusSeconds(120),
                NOW.minusSeconds(60)
        );

        // When & Then
        assertThatThrownBy(() -> new FeedbackSubmissionHistory(times).validateSubmissionAt(NOW))
                .hasMessageContaining(NOW.plusSeconds(86100).toString());
    }

    @Test
    @DisplayName("원본 목록을 비워도 이미 생성한 접수 이력의 제한은 유지된다")
    void validateSubmissionAt_originalListChanged_keepsSubmissionHistory() {
        // Given
        List<Instant> times = new ArrayList<>(List.of(NOW.minusSeconds(30)));
        FeedbackSubmissionHistory history = new FeedbackSubmissionHistory(times);

        // When
        times.clear();

        // Then
        assertThatThrownBy(() -> history.validateSubmissionAt(NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(NOW.plusSeconds(30).toString());
    }

    private List<Instant> dailyHistory(Instant oldest) {
        return List.of(
                oldest,
                NOW.minusSeconds(14400),
                NOW.minusSeconds(10800),
                NOW.minusSeconds(7200),
                NOW.minusSeconds(3600)
        );
    }
}
