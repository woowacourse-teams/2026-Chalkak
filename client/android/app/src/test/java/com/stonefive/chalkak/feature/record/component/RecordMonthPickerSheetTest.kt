package com.stonefive.chalkak.feature.record.component

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordMonthPickerSheetTest {
    @Test
    fun `연도를 바꾸면 그 해의 기록 달 중 가장 가까운 달을 고른다`() {
        val months = listOf(
            YearMonth.of(2026, 9),
            YearMonth.of(2026, 10),
            YearMonth.of(2027, 1),
            YearMonth.of(2027, 4),
        )

        assertEquals(YearMonth.of(2026, 9), closestRecordMonthInYear(months, 2026, 3))
        assertEquals(YearMonth.of(2027, 4), closestRecordMonthInYear(months, 2027, 10))
    }
}
