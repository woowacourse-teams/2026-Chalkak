package com.stonefive.chalkak.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeScrollBehaviorTest {
    @Test
    fun `로고 배경은 상단 영역이 거의 접힌 후에만 투명해진다`() {
        assertEquals(1f, topBarBackgroundAlpha(collapsedProgress = 0.8f))
        assertEquals(0.93f, topBarBackgroundAlpha(collapsedProgress = 0.9f), 0.001f)
        assertEquals(0.86f, topBarBackgroundAlpha(collapsedProgress = 1f))
    }

    @Test
    fun `상단 영역과 하단 바는 스크롤 거리만큼 이동한다`() {
        assertEquals(
            -32f,
            topAreaOffsetAfterScroll(
                currentOffset = 0f,
                scrollDelta = -32f,
                areaHeight = 100f,
            ),
        )
        assertEquals(
            32f,
            bottomBarOffsetAfterScroll(
                currentOffset = 0f,
                scrollDelta = -32f,
                barHeight = 80f,
            ),
        )
    }

    @Test
    fun `손가락을 위로 올리면 하단 바는 내려가고 플로팅 버튼은 표시된다`() {
        assertEquals(
            60f,
            bottomBarOffsetAfterScroll(
                currentOffset = 40f,
                scrollDelta = -20f,
                barHeight = 80f,
            ),
        )
        assertEquals(true, shouldShowScrollToTopButton(scrollDelta = -20f))
    }

    @Test
    fun `손가락을 아래로 내리면 하단 바는 올라오고 플로팅 버튼은 숨겨진다`() {
        assertEquals(
            20f,
            bottomBarOffsetAfterScroll(
                currentOffset = 40f,
                scrollDelta = 20f,
                barHeight = 80f,
            ),
        )
        assertEquals(false, shouldShowScrollToTopButton(scrollDelta = 20f))
    }

    @Test
    fun `절반 이하에서 손을 떼면 원래 위치로 돌아간다`() {
        assertEquals(
            0f,
            settleBarOffset(
                currentOffset = -50f,
                hiddenOffset = -100f,
                restingOffset = 0f,
            ),
        )
        assertEquals(
            0f,
            settleBarOffset(
                currentOffset = 40f,
                hiddenOffset = 100f,
                restingOffset = 0f,
            ),
        )
    }

    @Test
    fun `절반을 넘겨 손을 떼면 숨김 위치까지 이동한다`() {
        assertEquals(
            -100f,
            settleBarOffset(
                currentOffset = -51f,
                hiddenOffset = -100f,
                restingOffset = 0f,
            ),
        )
        assertEquals(
            100f,
            settleBarOffset(
                currentOffset = 60f,
                hiddenOffset = 100f,
                restingOffset = 0f,
            ),
        )
    }

    @Test
    fun `숨김 상태에서 절반 이하만 되돌리면 다시 숨김 위치로 돌아간다`() {
        assertEquals(
            -100f,
            settleBarOffset(
                currentOffset = -50f,
                hiddenOffset = -100f,
                restingOffset = -100f,
            ),
        )
        assertEquals(
            100f,
            settleBarOffset(
                currentOffset = 60f,
                hiddenOffset = 100f,
                restingOffset = 100f,
            ),
        )
    }
}
