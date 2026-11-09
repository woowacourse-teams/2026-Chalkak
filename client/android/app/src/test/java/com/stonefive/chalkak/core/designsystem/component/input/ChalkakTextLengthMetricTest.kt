package com.stonefive.chalkak.core.designsystem.component.input

import org.junit.Assert.assertEquals
import org.junit.Test

class ChalkakTextLengthMetricTest {
    @Test
    fun `code point 기준은 이모지를 서버처럼 여러 글자로 센다`() {
        // 국기 이모지는 한 글자로 보이지만 code point 두 개다.
        assertEquals(3, "가🇰🇷".length(ChalkakTextLengthMetric.CODE_POINTS))
    }

    @Test
    fun `code point 기준 제한은 최대 개수를 넘는 부분만 자른다`() {
        val text = "가".repeat(298) + "🇰🇷" + "끝"

        val limited = text.limited(300, ChalkakTextLengthMetric.CODE_POINTS)

        assertEquals("가".repeat(298) + "🇰🇷", limited)
        assertEquals("짧은 설명", "짧은 설명".limited(300, ChalkakTextLengthMetric.CODE_POINTS))
    }

    @Test
    fun `code point 기준 제한은 글자 중간을 자르지 않는다`() {
        val flag = "가".repeat(299) + "🇰🇷"
        val family = "가".repeat(296) + "👨‍👩‍👧"

        assertEquals("가".repeat(299), flag.limited(300, ChalkakTextLengthMetric.CODE_POINTS))
        assertEquals("가".repeat(296), family.limited(300, ChalkakTextLengthMetric.CODE_POINTS))
    }
}
