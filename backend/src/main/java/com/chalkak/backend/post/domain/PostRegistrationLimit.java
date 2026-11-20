package com.chalkak.backend.post.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.time.Instant;
import java.time.ZoneId;

/** 삭제·반려 여부와 무관하게 성공한 등록을 KST 날짜별로 제한한다. */
public record PostRegistrationLimit(
        int limit,
        long usedCount,
        int remainingCount,
        Instant resetsAt
) {

    private static final int DAILY_LIMIT = 10;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static PostRegistrationLimit from(long usedCount, Instant now) {
        return new PostRegistrationLimit(
                DAILY_LIMIT,
                usedCount,
                (int) Math.max(0, DAILY_LIMIT - usedCount),
                getNextDayStart(now)
        );
    }

    public static Instant getDayStart(Instant now) {
        return now.atZone(KST).toLocalDate().atStartOfDay(KST).toInstant();
    }

    public static Instant getNextDayStart(Instant now) {
        return now.atZone(KST).toLocalDate().plusDays(1).atStartOfDay(KST).toInstant();
    }

    public void validateCanRegister() {
        if (remainingCount == 0) {
            throw new BusinessException(
                    ErrorCode.POST_REGISTRATION_LIMIT_REACHED,
                    "사진은 하루에 총 10회까지 등록할 수 있습니다. 다음 날 다시 등록해 주세요.");
        }
    }
}
