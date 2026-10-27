package com.chalkak.backend.notification.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PushEventIdPolicy {

    private static final String OCCURRENCE_NAMESPACE = "chalkak:push-occurrence:v1:";
    private static final String AI_REVIEW_NAMESPACE = "AI_REVIEW:";

    public static UUID createForOccurrence(
            NotificationType type,
            UUID scheduleId,
            Instant occurredAt
    ) {
        // 실행 시각이나 문구가 달라도 같은 설정의 같은 회차는 같은 사건으로 식별한다.
        String key = OCCURRENCE_NAMESPACE + type.name() + ":" + scheduleId.toString() + ":"
                + occurredAt.toString();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    public static UUID createForAiReview(UUID aiReviewId, int round) {
        // 같은 리뷰 행을 회차마다 재사용하므로 리뷰 ID와 회차로 사건을 구분한다.
        // 이미 발행한 사건과 같은 UUID가 나오도록 기존 접두사와 UTF-8 형식을 유지한다.
        String key = AI_REVIEW_NAMESPACE + aiReviewId + ":" + round;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
