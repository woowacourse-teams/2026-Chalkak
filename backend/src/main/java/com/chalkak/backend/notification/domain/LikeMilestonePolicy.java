package com.chalkak.backend.notification.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LikeMilestonePolicy {

    private static final long INTERVAL = 5L;

    public static long calculateCandidate(long likeCount) {
        return likeCount / INTERVAL * INTERVAL;
    }

    public static void validate(long milestoneCount) {
        if (milestoneCount < INTERVAL || milestoneCount % INTERVAL != 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "좋아요 단계는 5 이상의 5단위여야 합니다.");
        }
    }
}
