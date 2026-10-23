package com.stonefive.chalkak.feature.photoedit.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulerScaleTest {
    private val scale = RulerScale(range = -1f..1f, anchor = 0.3f, detentHalfWidth = 0.02f)

    @Test
    fun `기준값 앞뒤 멈춤 구간 안에서는 기준값에 머문다`() {
        assertEquals(0.3f, scale.value(0.3f), 0f)
        assertEquals(0.3f, scale.value(0.319f), 0f)
        assertEquals(0.3f, scale.value(0.281f), 0f)
        assertTrue(scale.isAnchor(scale.value(0.31f)))
    }

    @Test
    fun `멈춤 구간을 지나면 구간만큼 뺀 값을 100분의 1 단위로 맞춘다`() {
        assertEquals(0.31f, scale.value(0.33f), TOLERANCE)
        assertEquals(0.29f, scale.value(0.2687f), TOLERANCE)
    }

    @Test
    fun `위치와 값은 서로 되돌릴 수 있고 범위 밖은 끝값에 멈춘다`() {
        listOf(-1f, -0.5f, 0f, 0.3f, 0.75f, 1f).forEach { value ->
            assertEquals(value, scale.value(scale.position(value)), TOLERANCE)
        }
        assertEquals(1f, scale.value(5f), 0f)
        assertEquals(-1f, scale.value(-5f), 0f)
    }

    @Test
    fun `기준값은 범위 안으로 자르고 눈금 칸은 0점02 단위다`() {
        val auto = RulerScale(range = 0f..1f, anchor = -0.4f, detentHalfWidth = 0.02f)
        assertEquals(0f, auto.anchor, 0f)
        assertEquals(RulerScale.tickOf(0.02f), RulerScale.tickOf(0.039f))
        assertFalse(RulerScale.tickOf(0.039f) == RulerScale.tickOf(0.04f))
    }

    private companion object {
        const val TOLERANCE = 1e-4f
    }
}
