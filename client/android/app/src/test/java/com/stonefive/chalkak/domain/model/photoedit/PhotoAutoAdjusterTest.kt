package com.stonefive.chalkak.domain.model.photoedit

import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoAutoAdjusterTest {
    @Test
    fun `대비가 낮고 뜬 사진은 블랙 포인트·대비·노출을 올려 밝기 범위를 넓힌다`() {
        val pixels = lowContrastPixels()

        val adjustments = PhotoAutoAdjuster.adjustments(PhotoImageStatistics.of(pixels))
        val adjusted = pixels.argb
            .copyOf()
            .also { PhotoColorTransform(adjustments).apply(it) }

        assertTrue(adjustments.blackPoint > 0f)
        assertTrue(adjustments.contrast > 0f)
        assertTrue(adjustments.exposure > 0f)
        assertTrue(brightnessRange(adjusted) > brightnessRange(pixels.argb) + 30)
    }

    @Test
    fun `밝기가 고르게 퍼진 사진은 여러 항목을 조금씩만 바꾸고 흑백 사진에는 채도를 올리지 않는다`() {
        val uniform = PhotoImageStatistics(
            luminanceHistogram = DoubleArray(256) { 1.0 / 256 },
            saturation90 = 0.6,
        )

        val adjustments = PhotoAutoAdjuster.adjustments(uniform)

        PhotoAdjustment.entries.forEach { adjustment ->
            assertTrue("$adjustment ${adjustments[adjustment]}", abs(adjustments[adjustment]) <= 0.35f)
        }
        // 문턱에 걸려 모두 0이 되지 않고, 자동 보정이 무엇을 바꿨는지 여러 항목에 보인다.
        assertTrue(PhotoAdjustment.entries.count { adjustments[it] != 0f } >= 3)
        assertTrue(adjustments.contrast >= 0f)

        val grayscale = PhotoImageStatistics(
            luminanceHistogram = DoubleArray(256) { 1.0 / 256 },
            saturation90 = 0.01,
        )
        assertEquals(0f, PhotoAutoAdjuster.adjustments(grayscale).saturation)
    }

    @Test
    fun `통계는 파랑·초록이 가장 큰 픽셀의 채도를 두 배로 센다`() {
        val red = PhotoImageStatistics.of(PhotoPixels(1, 1, intArrayOf(0xFF996666.toInt())))
        val blue = PhotoImageStatistics.of(PhotoPixels(1, 1, intArrayOf(0xFF666699.toInt())))

        assertEquals(0.2, red.saturation90, 0.01)
        assertEquals(0.4, blue.saturation90, 0.01)
        assertEquals(1.0, red.luminanceHistogram.sum(), 1e-9)
    }

    // iOS 테스트와 같은 64×64 사진: 색상 0.08, 채도 0.3이고 명도가 0.35에서 0.6까지 가로로 오르는 뜬 주황빛 회색.
    private fun lowContrastPixels(): PhotoPixels {
        val size = 64
        val argb = IntArray(size * size) { index ->
            val value = 0.35 + 0.25 * (index % size) / (size - 1)
            // HSB(0.08, 0.3, value)를 RGB로 바꾼다. 색상이 빨강과 노랑 사이라 빨강이 가장 크고 파랑이 가장 작다.
            val red = (value * 255).roundToInt()
            val green = (value * (1 - 0.3 * (1 - 0.48)) * 255).roundToInt()
            val blue = (value * 0.7 * 255).roundToInt()
            (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
        }
        return PhotoPixels(size, size, argb)
    }

    // iOS 테스트처럼 가장 밝은 픽셀과 가장 어두운 픽셀의 녹색 채널 차이.
    private fun brightnessRange(argb: IntArray): Int {
        val levels = argb.map { it shr 8 and 0xFF }
        return levels.max() - levels.min()
    }
}
