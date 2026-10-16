package com.stonefive.chalkak.feature.today

import org.junit.Assert.assertEquals
import org.junit.Test

class TodayScrollBehaviorTest {
    @Test
    fun `로고 배경은 상단 영역이 거의 접힌 후에만 투명해진다`() {
        assertEquals(1f, topBarBackgroundAlpha(collapsedProgress = 0.8f))
        assertEquals(0.93f, topBarBackgroundAlpha(collapsedProgress = 0.9f), 0.001f)
        assertEquals(0.86f, topBarBackgroundAlpha(collapsedProgress = 1f))
    }

    @Test
    fun `상단 영역은 스크롤 거리만큼 이동한다`() {
        assertEquals(
            -32f,
            topAreaOffsetAfterScroll(
                currentOffset = 0f,
                scrollDelta = -32f,
                areaHeight = 100f,
            ),
        )
    }
}
