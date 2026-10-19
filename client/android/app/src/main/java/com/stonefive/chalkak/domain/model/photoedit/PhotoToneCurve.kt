package com.stonefive.chalkak.domain.model.photoedit

import kotlin.math.PI
import kotlin.math.sin

/** 블랙 포인트·그림자·하이라이트·대비를 합친 톤 곡선. sRGB 감마 공간에서 채널마다 적용한다. */
object PhotoToneCurve {
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
        val inputBlack = PhotoAdjustmentResponse.inputBlack(blackPoint)
        val outputBlack = PhotoAdjustmentResponse.outputBlack(blackPoint)
        val shadows = adjustments.shadows.toDouble()
        val highlights = adjustments.highlights.toDouble()
        val contrast = adjustments.contrast.toDouble()

        var previous = 0.0
        return DoubleArray(SAMPLE_COUNT) { index ->
            var t = PhotoAdjustmentResponse.leveled(index.toDouble() / (SAMPLE_COUNT - 1), inputBlack)
            // 그림자는 1/3, 하이라이트는 2/3 부근만 볼록하게 움직이고 0과 1은 그대로 둔다.
            t += shadows * TONE_AMOUNT * 4 * t * (1 - t) * (1 - t)
            t += highlights * TONE_AMOUNT * 4 * t * t * (1 - t)
            // 0.5를 축으로 하는 S자 곡선.
            t -= contrast * CONTRAST_AMOUNT * sin(2 * PI * t) / (2 * PI)
            val y = outputBlack + (1 - outputBlack) * t.coerceIn(0.0, 1.0)
            previous = maxOf(previous, y)
            previous
        }
    }
}
