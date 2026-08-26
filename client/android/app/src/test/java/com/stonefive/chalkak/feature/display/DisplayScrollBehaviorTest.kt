package com.stonefive.chalkak.feature.display

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayScrollBehaviorTest {
    @Test
    fun `절반 미만 이동하면 원래 표시 상태로 돌아간다`() {
        assertEquals(
            0f,
            settleDisplayAreaOffset(
                currentOffset = -49f,
                hiddenOffset = -100f,
                restingOffset = 0f,
            ),
        )
    }

    @Test
    fun `절반을 넘겨 이동하면 진행 방향의 끝까지 이동한다`() {
        assertEquals(
            -100f,
            settleDisplayAreaOffset(
                currentOffset = -51f,
                hiddenOffset = -100f,
                restingOffset = 0f,
            ),
        )
        assertEquals(
            0f,
            settleDisplayAreaOffset(
                currentOffset = -49f,
                hiddenOffset = -100f,
                restingOffset = -100f,
            ),
        )
    }

    @Test
    fun `정확히 절반이면 직전 정착 상태를 유지한다`() {
        assertEquals(
            0f,
            settleDisplayAreaOffset(
                currentOffset = -50f,
                hiddenOffset = -100f,
                restingOffset = 0f,
            ),
        )
        assertEquals(
            -100f,
            settleDisplayAreaOffset(
                currentOffset = -50f,
                hiddenOffset = -100f,
                restingOffset = -100f,
            ),
        )
    }
}
