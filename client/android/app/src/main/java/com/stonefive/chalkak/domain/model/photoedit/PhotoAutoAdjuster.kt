package com.stonefive.chalkak.domain.model.photoedit

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign

/**
 * 앞 단계를 적용한 밝기 분포를 보고 다음 값을 정한다. 보정은 단조 증가라 백분위수 순서가 유지되므로
 * 히스토그램을 다시 세지 않고 위치만 옮긴다. 과보정을 피하려고 목표까지 다 옮기지 않고 덜어 낸다.
 */
object PhotoAutoAdjuster {
    private const val WHITE_PERCENTILE = 0.995
    private const val MEDIAN = 0.5
    private const val TARGET_WHITE = 0.96
    private const val MINIMUM_WHITE = 0.05
    private const val EXPOSURE_TEMPERING = 0.6
    private const val BRIGHT_MEDIAN = 0.62
    private const val BRIGHT_EXPOSURE_SLOPE = 1.2
    private const val MINIMUM_EXPOSURE_EV = -0.5
    private const val MAXIMUM_EXPOSURE_EV = 1.0

    private const val BLACK_PERCENTILE = 0.005
    private const val BLACK_MARGIN = 0.01
    private const val BLACK_TEMPERING = 0.8
    private const val MAXIMUM_INPUT_BLACK = 0.25

    private const val RESPONSE_EXPONENT = 0.7
    private const val DARK_LUMINANCE = 0.25
    private const val FULL_SHADOW_FRACTION = 0.8
    private const val MAXIMUM_SHADOW_LIFT = 0.45
    private const val BRIGHT_LUMINANCE = 0.85
    private const val FULL_HIGHLIGHT_FRACTION = 0.3
    private const val MAXIMUM_HIGHLIGHT_RECOVERY = 0.5

    private const val LOWER_QUARTILE = 0.25
    private const val UPPER_QUARTILE = 0.75
    private const val TARGET_MID_SPREAD = 0.42
    private const val CONTRAST_SLOPE = 2.0
    private const val MAXIMUM_CONTRAST = 0.4

    private const val GRAYSCALE_SATURATION = 0.05
    private const val TARGET_SATURATION = 0.7
    private const val SATURATION_SLOPE = 0.6
    private const val MAXIMUM_SATURATION = 0.25

    private const val ROUNDING_SCALE = 100.0
    private const val HALF = 0.5

    fun adjustments(statistics: PhotoImageStatistics): PhotoAdjustments {
        val exposure = exposure(statistics)
        val exposed = { luminance: Double -> expose(luminance, exposure) }
        val blackPoint = blackPoint(statistics, exposed)
        val inputBlack = PhotoAdjustmentResponse.inputBlack(blackPoint)
        val leveled = { luminance: Double -> PhotoAdjustmentResponse.leveled(exposed(luminance), inputBlack) }
        return PhotoAdjustments(
            exposure = exposure.toFloat(),
            contrast = contrast(statistics, leveled).toFloat(),
            highlights = highlights(statistics, leveled).toFloat(),
            shadows = shadows(statistics, leveled).toFloat(),
            blackPoint = blackPoint.toFloat(),
            saturation = saturation(statistics).toFloat(),
        )
    }

    // 흰 쪽이 비어 있으면 흰 점이 목표에 가까워지도록 올리고, 전체가 밝으면 조금 내린다.
    private fun exposure(statistics: PhotoImageStatistics): Double {
        val white = statistics.luminancePercentile(WHITE_PERCENTILE)
        val median = statistics.luminancePercentile(MEDIAN)
        val exposureEv = when {
            white < TARGET_WHITE -> {
                val gain = SrgbTransfer.linear(TARGET_WHITE) / SrgbTransfer.linear(max(white, MINIMUM_WHITE))
                ln(gain) / ln(2.0) * EXPOSURE_TEMPERING
            }

            median > BRIGHT_MEDIAN -> -(median - BRIGHT_MEDIAN) * BRIGHT_EXPOSURE_SLOPE

            else -> 0.0
        }
        val clamped = exposureEv.coerceIn(MINIMUM_EXPOSURE_EV, MAXIMUM_EXPOSURE_EV)
        return rounded(clamped / PhotoAdjustmentResponse.EXPOSURE_RANGE_EV)
    }

    private fun expose(
        luminance: Double,
        exposure: Double,
    ): Double {
        val linear = SrgbTransfer.linear(luminance) * PhotoAdjustmentResponse.exposureGain(exposure)
        return SrgbTransfer.gamma(minOf(1.0, linear))
    }

    private fun blackPoint(
        statistics: PhotoImageStatistics,
        exposed: (Double) -> Double,
    ): Double {
        val black = exposed(statistics.luminancePercentile(BLACK_PERCENTILE))
        val inputBlack = minOf((black - BLACK_MARGIN).coerceAtLeast(0.0) * BLACK_TEMPERING, MAXIMUM_INPUT_BLACK)
        return rounded(inputBlack / PhotoAdjustmentResponse.BLACK_POINT_RANGE)
    }

    private fun shadows(
        statistics: PhotoImageStatistics,
        leveled: (Double) -> Double,
    ): Double {
        val darkFraction = statistics.fraction { leveled(it) < DARK_LUMINANCE }
        return rounded(MAXIMUM_SHADOW_LIFT * response(darkFraction / FULL_SHADOW_FRACTION))
    }

    private fun highlights(
        statistics: PhotoImageStatistics,
        leveled: (Double) -> Double,
    ): Double {
        val brightFraction = statistics.fraction { leveled(it) > BRIGHT_LUMINANCE }
        return rounded(-MAXIMUM_HIGHLIGHT_RECOVERY * response(brightFraction / FULL_HIGHLIGHT_FRACTION))
    }

    private fun contrast(
        statistics: PhotoImageStatistics,
        leveled: (Double) -> Double,
    ): Double {
        val midSpread = leveled(statistics.luminancePercentile(UPPER_QUARTILE)) -
            leveled(statistics.luminancePercentile(LOWER_QUARTILE))
        return rounded(((TARGET_MID_SPREAD - midSpread) * CONTRAST_SLOPE).coerceIn(0.0, MAXIMUM_CONTRAST))
    }

    private fun saturation(statistics: PhotoImageStatistics): Double {
        val saturation = statistics.saturation90
        if (saturation <= GRAYSCALE_SATURATION) return 0.0
        return rounded(((TARGET_SATURATION - saturation) * SATURATION_SLOPE).coerceIn(0.0, MAXIMUM_SATURATION))
    }

    // 좁은 영역에도 반응하도록 1보다 작은 지수로 올린다.
    private fun response(ratio: Double): Double = ratio.coerceIn(0.0, 1.0).pow(RESPONSE_EXPONENT)

    // 0.5는 0에서 먼 쪽으로 반올림하고 -0은 0으로 바꾼다.
    private fun rounded(value: Double): Double =
        sign(value) * floor(abs(value) * ROUNDING_SCALE + HALF) / ROUNDING_SCALE + 0.0
}
