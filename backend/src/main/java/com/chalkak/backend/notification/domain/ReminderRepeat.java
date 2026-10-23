package com.chalkak.backend.notification.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

public enum ReminderRepeat {
    // 발송 시각에 한 번, 발송 전에만 수정
    ONCE((scheduledAt, now) -> now.isBefore(scheduledAt), (scheduledAt, now) -> scheduledAt),
    // 발송 시각의 날짜부터 매일 같은 KST 시각, 언제든 수정
    DAILY((scheduledAt, now) -> true, ReminderRepeat::findTodayOccurrence);

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BiPredicate<Instant, Instant> updatable;
    private final BiFunction<Instant, Instant, Instant> occurrenceFinder;

    ReminderRepeat(
            BiPredicate<Instant, Instant> updatable,
            BiFunction<Instant, Instant, Instant> occurrenceFinder
    ) {
        this.updatable = updatable;
        this.occurrenceFinder = occurrenceFinder;
    }

    public boolean canUpdate(Instant scheduledAt, Instant now) {
        return updatable.test(scheduledAt, now);
    }

    // 지금 기준으로 보낼 수 있는 회차 시각이다. 없으면 null을 돌려준다.
    public Instant findOccurrence(Instant scheduledAt, Instant now) {
        return occurrenceFinder.apply(scheduledAt, now);
    }

    // 오늘 KST 날짜의 같은 시각이다. 시작 시각 전이면 아직 시작하지 않았으므로 회차가 없다.
    private static Instant findTodayOccurrence(Instant scheduledAt, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, KST);
        Instant occurrence = today.atTime(LocalTime.ofInstant(scheduledAt, KST))
                .atZone(KST)
                .toInstant();
        if (occurrence.isBefore(scheduledAt)) {
            return null;
        }
        return occurrence;
    }
}
