package com.chalkak.backend.notification.domain;

import java.time.Instant;
import java.util.function.BiPredicate;

public enum ReminderRepeat {
    ONCE((scheduledAt, now) -> now.isBefore(scheduledAt)), // 발송 시각에 한 번, 발송 전에만 수정
    DAILY((scheduledAt, now) -> true); // 발송 시각의 날짜부터 매일 같은 KST 시각, 언제든 수정

    private final BiPredicate<Instant, Instant> updatable;

    ReminderRepeat(BiPredicate<Instant, Instant> updatable) {
        this.updatable = updatable;
    }

    public boolean canUpdate(Instant scheduledAt, Instant now) {
        return updatable.test(scheduledAt, now);
    }
}
