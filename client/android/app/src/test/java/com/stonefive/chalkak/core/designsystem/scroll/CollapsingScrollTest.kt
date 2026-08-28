package com.stonefive.chalkak.core.designsystem.scroll

import org.junit.Assert.assertEquals
import org.junit.Test

class CollapsingScrollTest {
    @Test
    fun `아래로 스크롤하면 하단 바가 내려가고 위로 스크롤하면 올라온다`() {
        assertEquals(
            32f,
            bottomBarOffsetAfterScroll(
                currentOffset = 0f,
                scrollDelta = -32f,
                barHeight = 80f,
            ),
        )
        assertEquals(
            20f,
            bottomBarOffsetAfterScroll(
                currentOffset = 40f,
                scrollDelta = 20f,
                barHeight = 80f,
            ),
        )
    }

    @Test
    fun `하단 바 이동은 0과 높이 사이로 제한된다`() {
        assertEquals(
            60f,
            bottomBarOffsetAfterScroll(
                currentOffset = 40f,
                scrollDelta = -20f,
                barHeight = 80f,
            ),
        )
    }

    @Test
    fun `같은 방향 누적 스크롤이 임계값을 넘어야 플로팅 버튼이 토글된다`() {
        val hidden = ScrollToTopButtonState(accumulated = 0f, visible = false)

        val small = scrollToTopButtonStateAfterScroll(hidden, scrollDelta = 5f, threshold = 12f)
        assertEquals(false, small.visible)

        val shown = scrollToTopButtonStateAfterScroll(small, scrollDelta = 8f, threshold = 12f)
        assertEquals(true, shown.visible)
    }

    @Test
    fun `반대 방향으로 임계값을 넘으면 숨겨지고 방향 전환 시 누적이 리셋된다`() {
        val shown = ScrollToTopButtonState(accumulated = 13f, visible = true)

        val stillShown = scrollToTopButtonStateAfterScroll(shown, scrollDelta = -5f, threshold = 12f)
        assertEquals(true, stillShown.visible)
        assertEquals(-5f, stillShown.accumulated)

        val hidden = scrollToTopButtonStateAfterScroll(stillShown, scrollDelta = -8f, threshold = 12f)
        assertEquals(false, hidden.visible)
    }

    @Test
    fun `임계값 이하로만 움직이면 원래 위치로 돌아간다`() {
        assertEquals(
            0f,
            settleCollapsingOffset(
                currentOffset = -3f,
                hiddenOffset = -100f,
                restingOffset = 0f,
            ),
        )
        assertEquals(
            0f,
            settleCollapsingOffset(
                currentOffset = 3f,
                hiddenOffset = 100f,
                restingOffset = 0f,
            ),
        )
    }

    @Test
    fun `임계값만 넘겨도 숨김 위치까지 이동한다`() {
        assertEquals(
            -100f,
            settleCollapsingOffset(
                currentOffset = -6f,
                hiddenOffset = -100f,
                restingOffset = 0f,
            ),
        )
        assertEquals(
            100f,
            settleCollapsingOffset(
                currentOffset = 6f,
                hiddenOffset = 100f,
                restingOffset = 0f,
            ),
        )
    }

    @Test
    fun `숨김 상태에서 임계값 이하로만 되돌리면 다시 숨김 위치로 돌아간다`() {
        assertEquals(
            -100f,
            settleCollapsingOffset(
                currentOffset = -97f,
                hiddenOffset = -100f,
                restingOffset = -100f,
            ),
        )
        assertEquals(
            100f,
            settleCollapsingOffset(
                currentOffset = 97f,
                hiddenOffset = 100f,
                restingOffset = 100f,
            ),
        )
    }

    @Test
    fun `숨김 상태에서 임계값만 넘겨 되돌리면 다시 나타난다`() {
        assertEquals(
            0f,
            settleCollapsingOffset(
                currentOffset = -94f,
                hiddenOffset = -100f,
                restingOffset = -100f,
            ),
        )
        assertEquals(
            0f,
            settleCollapsingOffset(
                currentOffset = 94f,
                hiddenOffset = 100f,
                restingOffset = 100f,
            ),
        )
    }
}
