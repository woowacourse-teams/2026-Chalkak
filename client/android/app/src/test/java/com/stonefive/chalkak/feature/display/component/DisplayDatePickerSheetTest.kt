package com.stonefive.chalkak.feature.display.component

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayDatePickerSheetTest {
    @Test
    fun `첫 해와 마지막 해는 범위 안의 월만 고를 수 있다`() {
        val startDate = LocalDate.of(2026, 9, 17)
        val endDate = LocalDate.of(2027, 2, 3)

        assertEquals(listOf(9, 10, 11, 12), displayPickerMonths(2026, startDate, endDate))
        assertEquals(listOf(1, 2), displayPickerMonths(2027, startDate, endDate))
    }

    @Test
    fun `첫 달은 첫 주제 날짜부터, 마지막 달은 오늘까지, 그 사이 달은 말일까지 고를 수 있다`() {
        val startDate = LocalDate.of(2026, 9, 17)
        val endDate = LocalDate.of(2026, 11, 5)

        assertEquals((17..30).toList(), displayPickerDays(YearMonth.of(2026, 9), startDate, endDate))
        assertEquals((1..31).toList(), displayPickerDays(YearMonth.of(2026, 10), startDate, endDate))
        assertEquals((1..5).toList(), displayPickerDays(YearMonth.of(2026, 11), startDate, endDate))
    }

    @Test
    fun `그 달에 없는 날은 말일로, 범위 밖 날짜는 범위 끝으로 맞춘다`() {
        val startDate = LocalDate.of(2026, 9, 17)
        val endDate = LocalDate.of(2026, 10, 22)

        assertEquals(LocalDate.of(2026, 9, 30), displayPickerDate(2026, 9, 31, startDate, endDate))
        assertEquals(startDate, displayPickerDate(2026, 9, 3, startDate, endDate))
        assertEquals(endDate, displayPickerDate(2026, 10, 30, startDate, endDate))
    }
}
