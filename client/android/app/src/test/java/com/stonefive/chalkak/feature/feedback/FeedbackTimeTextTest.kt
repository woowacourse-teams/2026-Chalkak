package com.stonefive.chalkak.feature.feedback

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedbackTimeTextTest {
    @Test
    fun `문의·답변 시각은 오늘·어제·그 전으로 나눠 표시한다`() {
        // Given  지금이 2026-11-05T07:00:00Z(서울 16:00)
        val clock = Clock.fixed(Instant.parse("2026-11-05T07:00:00Z"), ZoneOffset.UTC)

        // When   2026-11-05T00:30:00Z / 2026-11-04T09:20:00Z / 2026-11-03T05:20:00Z를 표시한다
        val today = "2026-11-05T00:30:00Z".toFeedbackTimeText(clock)
        val yesterday = "2026-11-04T09:20:00Z".toFeedbackTimeText(clock)
        val earlier = "2026-11-03T05:20:00Z".toFeedbackTimeText(clock)

        // Then   "09:30" / "어제 18:20" / "11월 3일 14:20"
        assertEquals("09:30", today)
        assertEquals("어제 18:20", yesterday)
        assertEquals("11월 3일 14:20", earlier)
    }
}
