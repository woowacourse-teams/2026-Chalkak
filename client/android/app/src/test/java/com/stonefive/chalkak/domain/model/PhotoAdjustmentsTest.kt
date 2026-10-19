package com.stonefive.chalkak.domain.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoAdjustmentsTest {
    @Test
    fun `조정하지 않으면 색 행렬이 없다`() {
        assertNull(PhotoAdjustments().colorMatrix())
        assertNull(PhotoAdjustments(vignette = 0.5f).colorMatrix())
        assertNull(PhotoEdit().colorMatrix())
    }

    @Test
    fun `밝기는 모든 채널에 같은 값을 더한다`() {
        val matrix = PhotoAdjustments(brightness = 0.4f).colorMatrix()!!

        assertColor(floatArrayOf(125.5f, 75.5f, 25.5f), apply(matrix, 100f, 50f, 0f))
    }

    @Test
    fun `대비는 중간 회색을 유지한다`() {
        val matrix = PhotoAdjustments(contrast = 0.8f).colorMatrix()!!

        assertColor(floatArrayOf(128f, 128f, 128f), apply(matrix, 128f, 128f, 128f))
        assertTrue(apply(matrix, 200f, 200f, 200f)[0] > 200f)
        assertTrue(apply(matrix, 50f, 50f, 50f)[0] < 50f)
    }

    @Test
    fun `화이트 포인트는 검정을, 블랙 포인트는 흰색을 유지한다`() {
        val white = PhotoAdjustments(whitePoint = 0.6f).colorMatrix()!!
        val black = PhotoAdjustments(blackPoint = 0.6f).colorMatrix()!!

        assertColor(floatArrayOf(0f, 0f, 0f), apply(white, 0f, 0f, 0f))
        assertTrue(apply(white, 200f, 200f, 200f)[0] > 200f)
        assertColor(floatArrayOf(255f, 255f, 255f), apply(black, 255f, 255f, 255f))
        assertTrue(apply(black, 50f, 50f, 50f)[0] < 50f)
    }

    @Test
    fun `채도를 끝까지 내리면 휘도가 같은 회색이 된다`() {
        val matrix = PhotoAdjustments(saturation = -1f).colorMatrix()!!

        val gray = apply(matrix, 200f, 100f, 50f)

        val luma = 0.299f * 200f + 0.587f * 100f + 0.114f * 50f
        assertColor(floatArrayOf(luma, luma, luma), gray)
    }

    @Test
    fun `따뜻함은 빨강을 올리고 파랑을 내리며, 색조는 초록을 옮긴다`() {
        val warm = apply(PhotoAdjustments(warmth = 1f).colorMatrix()!!, 100f, 100f, 100f)
        val magenta = apply(PhotoAdjustments(tint = 1f).colorMatrix()!!, 100f, 100f, 100f)

        assertTrue(warm[0] > 100f && warm[2] < 100f)
        assertEquals(100f, warm[1], TOLERANCE)
        assertTrue(magenta[1] < 100f)
    }

    @Test
    fun `값은 -1에서 1로 제한한다`() {
        val adjustments = PhotoAdjustments()
            .with(PhotoAdjustment.Contrast, 3f)
            .with(PhotoAdjustment.Vignette, -2f)

        assertEquals(1f, adjustments[PhotoAdjustment.Contrast], TOLERANCE)
        assertEquals(-1f, adjustments[PhotoAdjustment.Vignette], TOLERANCE)
        assertEquals(0.7f, adjustments.vignetteAlpha, TOLERANCE)
    }

    @Test
    fun `편집 색 행렬은 조정을 먼저, 필터를 나중에 적용한다`() {
        val edit = PhotoEdit(
            filter = PhotoFilter.Mono,
            adjustments = PhotoAdjustments(warmth = 1f),
        )

        val combined = apply(edit.colorMatrix()!!, 100f, 100f, 100f)

        val warmed = apply(edit.adjustments.colorMatrix()!!, 100f, 100f, 100f)
        val expected = apply(PhotoFilter.Mono.colorMatrix()!!, warmed[0], warmed[1], warmed[2])
        assertColor(expected, combined)
    }

    private fun apply(
        matrix: FloatArray,
        red: Float,
        green: Float,
        blue: Float,
    ): FloatArray {
        val color = floatArrayOf(red, green, blue, 255f)
        return FloatArray(3) { row ->
            (0 until 4).fold(matrix[row * 5 + 4]) { sum, k -> sum + matrix[row * 5 + k] * color[k] }
        }
    }

    private fun assertColor(
        expected: FloatArray,
        actual: FloatArray,
    ) = assertArrayEquals(expected, actual, TOLERANCE)

    private companion object {
        const val TOLERANCE = 1e-2f
    }
}
