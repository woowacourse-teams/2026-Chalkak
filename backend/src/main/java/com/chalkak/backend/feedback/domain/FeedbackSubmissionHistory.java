package com.chalkak.backend.feedback.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class FeedbackSubmissionHistory {

    private static final Duration MIN_INTERVAL = Duration.ofMinutes(1);
    private static final Duration DAILY_WINDOW = Duration.ofHours(24);
    private static final int DAILY_LIMIT = 5;

    private final List<Instant> submissionTimes;

    public FeedbackSubmissionHistory(List<Instant> submissionTimes) {
        this.submissionTimes = submissionTimes.stream()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    public static Instant getWindowStart(Instant now) {
        return now.minus(DAILY_WINDOW);
    }

    public void validateSubmissionAt(Instant now) {
        if (submissionTimes.isEmpty()) {
            return;
        }

        Instant nextAllowedAt = submissionTimes.getFirst().plus(MIN_INTERVAL);
        if (submissionTimes.size() >= DAILY_LIMIT) {
            // 다섯 건보다 많은 기존 이력도, 최근 다섯 번째 접수가 만료돼야 새 한 건을 받을 수 있다.
            Instant dailyAvailableAt = submissionTimes.get(DAILY_LIMIT - 1).plus(DAILY_WINDOW);
            if (dailyAvailableAt.isAfter(nextAllowedAt)) {
                nextAllowedAt = dailyAvailableAt;
            }
        }
        if (now.isBefore(nextAllowedAt)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "문의는 1분에 1건, 최근 24시간에 최대 5건 접수할 수 있습니다. "
                            + "다음 접수 가능 시각(UTC): " + nextAllowedAt
            );
        }
    }
}
