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
    fun `손가락을 위로 올리면 하단 바는 내려간다`() {
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
    fun `손가락을 아래로 내리면 하단 바는 올라온다`() {
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
    fun `같은 방향 누적 스크롤이 임계값을 넘어야 플로팅 버튼이 토글된다`() {
        val hidden = ScrollToTopButtonState(accumulated = 0f, visible = false)

        // 임계값(12) 미만이면 아직 표시되지 않는다.
        val small = scrollToTopButtonStateAfterScroll(hidden, scrollDelta = 5f, threshold = 12f)
        assertEquals(false, small.visible)

        // 같은 방향으로 더 누적되어 임계값을 넘으면 표시된다.
        val shown = scrollToTopButtonStateAfterScroll(small, scrollDelta = 8f, threshold = 12f)
        assertEquals(true, shown.visible)
    }

    @Test
    fun `반대 방향으로 임계값을 넘으면 숨겨지고 방향 전환 시 누적이 리셋된다`() {
        val shown = ScrollToTopButtonState(accumulated = 13f, visible = true)

        // 방향이 바뀌면 누적이 리셋되므로, 한 프레임의 작은 역방향으로는 숨겨지지 않는다.
        val stillShown = scrollToTopButtonStateAfterScroll(shown, scrollDelta = -5f, threshold = 12f)
        assertEquals(true, stillShown.visible)
        assertEquals(-5f, stillShown.accumulated)

        // 역방향으로 임계값을 넘으면 숨겨진다.
        val hidden = scrollToTopButtonStateAfterScroll(stillShown, scrollDelta = -8f, threshold = 12f)
        assertEquals(false, hidden.visible)
    }

    @Test
    fun `임계값 이하로만 움직이면 원래 위치로 돌아간다`() {
        assertEquals(
            0f,
            settleBarOffset(
                currentOffset = -3f,
                hiddenOffset = -100f,
                restingOffset = 0f,
            ),
        )
        assertEquals(
            0f,
            settleBarOffset(
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
            settleBarOffset(
                currentOffset = -6f,
                hiddenOffset = -100f,
                restingOffset = 0f,
            ),
        )
        assertEquals(
            100f,
            settleBarOffset(
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
            settleBarOffset(
                currentOffset = -97f,
                hiddenOffset = -100f,
                restingOffset = -100f,
            ),
        )
        assertEquals(
            100f,
            settleBarOffset(
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
            settleBarOffset(
                currentOffset = -94f,
                hiddenOffset = -100f,
                restingOffset = -100f,
            ),
        )
        assertEquals(
            0f,
            settleBarOffset(
                currentOffset = 94f,
                hiddenOffset = 100f,
                restingOffset = 100f,
            ),
        )
    }
}
