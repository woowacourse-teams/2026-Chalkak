package com.stonefive.chalkak.data.local.reminder

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderAlarmSchedulerTest {
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun `설정 시간이 남아 있으면 오늘 시간을 예약한다`() {
        val now = ZonedDateTime.of(2026, 9, 22, 17, 30, 0, 0, zone)

        val triggerAt = nextReminderTriggerAtMillis(hour = 18, minute = 0, now = now)

        assertEquals(
            ZonedDateTime
                .of(2026, 9, 22, 18, 0, 0, 0, zone)
                .toInstant()
                .toEpochMilli(),
            triggerAt,
        )
    }

    @Test
    fun `설정 시간이 지났으면 다음 날 시간을 예약한다`() {
        val now = ZonedDateTime.of(2026, 9, 22, 18, 0, 0, 0, zone)

        val triggerAt = nextReminderTriggerAtMillis(hour = 18, minute = 0, now = now)

        assertEquals(
            ZonedDateTime
                .of(2026, 9, 23, 18, 0, 0, 0, zone)
                .toInstant()
                .toEpochMilli(),
            triggerAt,
        )
    }
}
