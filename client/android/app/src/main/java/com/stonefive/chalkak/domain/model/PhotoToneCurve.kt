package com.stonefive.chalkak.domain.model

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * 블랙 포인트·그림자·하이라이트·대비를 하나의 톤 곡선으로 만든다. 곡선은 sRGB 감마 공간에서 채널마다 적용한다.
 * iOS `PhotoEditToneCurve`와 같은 식과 상수를 쓴다.
 */
object PhotoToneCurve {
    /** 노출 1.0이 올리는 EV. */
    const val EXPOSURE_RANGE_EV = 1.5

    /** 블랙 포인트 1.0이 검게 만드는 입력 밝기. -1.0이면 같은 만큼 검은색을 띄운다. */
    const val BLACK_POINT_RANGE = 0.3

    /** 따뜻함 1.0이 빨강을 올리고 파랑을 내리는 비율(선형 공간). */
    const val WARMTH_GAIN = 0.25
    const val SAMPLE_COUNT = 256

    private const val TONE_AMOUNT = 0.25
    private const val CONTRAST_AMOUNT = 0.6

    fun isIdentity(adjustments: PhotoAdjustments): Boolean = adjustments.blackPoint == 0f &&
        adjustments.shadows == 0f &&
        adjustments.highlights == 0f &&
        adjustments.contrast == 0f

    /** 0..1 입력을 [SAMPLE_COUNT]개로 나눈 출력값. 어떤 조합에서도 줄어들지 않는다. */
    fun samples(adjustments: PhotoAdjustments): DoubleArray {
        val blackPoint = adjustments.blackPoint.toDouble()
        val inputBlack = blackPoint.coerceAtLeast(0.0) * BLACK_POINT_RANGE
        val outputBlack = (-blackPoint).coerceAtLeast(0.0) * BLACK_POINT_RANGE
        val shadows = adjustments.shadows.toDouble()
        val highlights = adjustments.highlights.toDouble()
        val contrast = adjustments.contrast.toDouble()

        var previous = 0.0
        return DoubleArray(SAMPLE_COUNT) { index ->
            val x = index.toDouble() / (SAMPLE_COUNT - 1)
            var t = ((x - inputBlack) / (1 - inputBlack)).coerceIn(0.0, 1.0)
            // 어두운 쪽(1/3 부근)과 밝은 쪽(2/3 부근)만 볼록하게 움직이고 0과 1은 그대로 둔다.
            t += shadows * TONE_AMOUNT * 4 * t * (1 - t) * (1 - t)
            t += highlights * TONE_AMOUNT * 4 * t * t * (1 - t)
            // 0.5를 축으로 하는 S자 곡선.
            t -= contrast * CONTRAST_AMOUNT * sin(2 * PI * t) / (2 * PI)
            val y = outputBlack + (1 - outputBlack) * t.coerceIn(0.0, 1.0)
            previous = maxOf(previous, y)
            previous
        }
    }

    fun linear(value: Double): Double = if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)

    fun gamma(value: Double): Double = if (value <= 0.0031308) value * 12.92 else 1.055 * value.pow(1 / 2.4) - 0.055
}
