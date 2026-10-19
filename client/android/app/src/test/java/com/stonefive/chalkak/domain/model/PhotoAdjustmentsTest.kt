package com.stonefive.chalkak.domain.model

import kotlin.math.pow
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoAdjustmentsTest {
    @Test
    fun `음수 0은 0과 같은 값으로 둔다`() {
        assertTrue(PhotoAdjustments().with(PhotoAdjustment.Highlights, -0f).isIdentity)
    }

    @Test
    fun `강도를 곱하고 더한 값은 항목마다 -1에서 1로 자른다`() {
        val automatic = PhotoAdjustments(exposure = 0.6f, shadows = -0.4f)
        val manual = PhotoAdjustments(exposure = 0.8f, contrast = 0.2f)

        val applied = automatic.scaled(scale = 0.5f, offset = manual)

        assertEquals(PhotoAdjustments(exposure = 1f, contrast = 0.2f, shadows = -0.2f), applied)
    }

    @Test
    fun `톤 곡선은 어떤 조합에서도 0에서 시작해 1로 끝나고 줄어들지 않는다`() {
        val extremes = listOf(-1f, 0f, 1f)
        for (blackPoint in extremes) {
            for (shadows in extremes) {
                for (highlights in extremes) {
                    for (contrast in extremes) {
                        val samples = PhotoToneCurve.samples(
                            PhotoAdjustments(
                                blackPoint = blackPoint,
                                shadows = shadows,
                                highlights = highlights,
                                contrast = contrast,
                            ),
                        )
                        assertTrue(
                            samples
                                .toList()
                                .zipWithNext()
                                .all { (first, second) -> first <= second },
                        )
                        assertEquals(1.0, samples.last(), 0.0)
                        if (blackPoint >= 0f) assertEquals(0.0, samples.first(), 0.0)
                    }
                }
            }
        }
        assertTrue(PhotoToneCurve.isIdentity(PhotoAdjustments(exposure = 0.5f, warmth = 0.2f)))
    }

    @Test
    fun `조정과 필터가 없으면 픽셀을 바꾸지 않는다`() {
        val pixels = intArrayOf(0xFF102030.toInt(), 0x80FFFFFF.toInt())

        PhotoColorTransform(PhotoAdjustments()).apply(pixels)

        assertEquals(listOf(0xFF102030.toInt(), 0x80FFFFFF.toInt()), pixels.toList())
    }

    @Test
    fun `노출을 올리면 밝아지고 검은색과 알파는 그대로다`() {
        val pixels = intArrayOf(gray(GRAY_LEVEL), gray(0), GRAY_LEVEL shl ALPHA_SHIFT or 0x646464)

        PhotoColorTransform(PhotoAdjustments(exposure = 0.4f)).apply(pixels)

        // 선형 공간에서 2^(0.4 × 1.5)배 한 뒤 다시 감마 인코딩한 값.
        val linear = SrgbTransfer.linear(GRAY_LEVEL / 255.0) * 2.0.pow(0.4 * PhotoAdjustmentResponse.EXPOSURE_RANGE_EV)
        assertEquals((SrgbTransfer.gamma(linear) * 255).roundToInt(), red(pixels[0]))
        assertEquals(gray(0), pixels[1])
        assertEquals(GRAY_LEVEL, pixels[2] ushr ALPHA_SHIFT)
    }

    @Test
    fun `따뜻함을 올리면 회색의 빨강이 파랑보다 커진다`() {
        val pixels = intArrayOf(gray(128))

        PhotoColorTransform(PhotoAdjustments(warmth = 1f)).apply(pixels)

        assertTrue(red(pixels[0]) - blue(pixels[0]) > 20)
    }

    @Test
    fun `채도를 -1로 내리면 흑백이 되고 조정 뒤에 필터를 적용한다`() {
        val pixels = intArrayOf(0xFFCC4020.toInt())

        PhotoColorTransform(PhotoAdjustments(saturation = -1f)).apply(pixels)

        assertEquals(red(pixels[0]), green(pixels[0]))
        assertEquals(green(pixels[0]), blue(pixels[0]))

        val filtered = intArrayOf(gray(GRAY_LEVEL))
        PhotoColorTransform(PhotoAdjustments(exposure = 0.001f), PhotoFilter.Warm.colorMatrix()).apply(filtered)
        assertTrue(red(filtered[0]) > blue(filtered[0]))
    }

    @Test
    fun `회전·자르기를 적용한 영역만 골라 줄인다`() {
        // 왼쪽 절반은 빨강, 오른쪽 절반은 파랑인 4×2 사진.
        val source = PhotoPixels(
            width = 4,
            height = 2,
            argb = IntArray(8) { index -> if (index % 4 < 2) RED else BLUE },
        )

        val left = source.sampled(
            PhotoEdit(crop = CropWindow(centerX = 0.25f, centerY = 0.5f, width = 0.5f, height = 1f)),
            maxLongEdge = 256,
        )
        assertEquals(2, left.width)
        assertEquals(2, left.height)
        assertTrue(left.argb.all { it == RED })

        // 시계 방향으로 돌리면 왼쪽 절반이 위쪽으로 온다.
        val turned = source.sampled(PhotoEdit(quarterTurns = 1), maxLongEdge = 256)
        assertEquals(2, turned.width)
        assertEquals(4, turned.height)
        assertTrue(
            turned.argb
                .take(4)
                .all { it == RED },
        )
        assertTrue(
            turned.argb
                .drop(4)
                .all { it == BLUE },
        )
    }

    private fun gray(level: Int): Int = (0xFF shl ALPHA_SHIFT) or (level shl 16) or (level shl 8) or level

    private fun red(color: Int) = color shr 16 and 0xFF

    private fun green(color: Int) = color shr 8 and 0xFF

    private fun blue(color: Int) = color and 0xFF

    private companion object {
        const val GRAY_LEVEL = 100
        const val ALPHA_SHIFT = 24
        const val RED = 0xFFFF0000.toInt()
        const val BLUE = 0xFF0000FF.toInt()
    }
}
