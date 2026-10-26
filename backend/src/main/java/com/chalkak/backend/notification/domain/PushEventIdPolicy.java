package com.chalkak.backend.notification.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PushEventIdPolicy {

    private static final String OCCURRENCE_NAMESPACE = "chalkak:push-occurrence:v1:";

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
}
