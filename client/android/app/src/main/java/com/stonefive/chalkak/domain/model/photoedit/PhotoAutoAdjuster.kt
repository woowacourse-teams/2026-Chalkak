package com.stonefive.chalkak.domain.model.photoedit

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign

/**
 * 사진 밝기·채도 통계([PhotoImageStatistics])를 보고 자동 보정값을 정한다. 편집 화면에서 자동 보정을 켜면 쓴다.
 *
 * 노출 → 블랙 포인트 → 그림자·하이라이트·대비 순서로 정하고, 각 단계는 앞 단계를 적용했다고 가정한 밝기를 본다.
 * 예: 노출을 올리기로 정했으면, 블랙 포인트는 "노출을 올린 뒤 가장 어두운 쪽"을 기준으로 정한다.
 * 이때 보정한 사진의 통계를 다시 세지 않고, 원래 히스토그램의 밝기 값만 앞 단계 식에 넣어 옮겨 본다.
 * 보정은 밝은 픽셀을 어두운 픽셀보다 어둡게 만들지 않아 순서는 유지되지만, 채널별 계산을 밝기 하나로 대신하므로 근사다.
 *
 * 과하게 바뀌지 않도록 목표까지 다 옮기지 않는다. `*_TEMPERING`은 옮길 비율(0.6이면 필요한 양의 60%),
 * `MAXIMUM_*`는 상한이다. 상수들은 튜닝 값이며, 바꾸면 PhotoColorGoldenTest의 기대값도 바뀐다.
 */
object PhotoAutoAdjuster {
    // 가장 밝은 0.5%는 반사광으로 보고 뺀 나머지 중 가장 밝은 값을 흰 점으로 본다.
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
        // 원래 밝기 → 노출을 적용한 뒤 밝기
        val afterExposure = { luminance: Double -> expose(luminance, exposure) }
        val blackPoint = blackPoint(statistics, afterExposure)
        val inputBlack = PhotoAdjustmentScale.inputBlack(blackPoint)
        // 원래 밝기 → 노출과 블랙 포인트를 적용한 뒤 밝기
        val afterBlackPoint = { luminance: Double ->
            PhotoAdjustmentScale.stretchAboveBlack(
                afterExposure(luminance),
                inputBlack,
            )
        }
        return PhotoAdjustments(
            exposure = exposure.toFloat(),
            contrast = contrast(statistics, afterBlackPoint).toFloat(),
            highlights = highlights(statistics, afterBlackPoint).toFloat(),
            shadows = shadows(statistics, afterBlackPoint).toFloat(),
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
        return rounded(clamped / PhotoAdjustmentScale.EXPOSURE_RANGE_EV)
    }

    private fun expose(
        luminance: Double,
        exposure: Double,
    ): Double {
        val linear = SrgbTransfer.linear(luminance) * PhotoAdjustmentScale.exposureGain(exposure)
        return SrgbTransfer.gamma(minOf(1.0, linear))
    }

    // 가장 어두운 쪽이 검정이 아니라 회색으로 떠 있으면, 떠 있는 만큼 일부를 검게 만든다.
    private fun blackPoint(
        statistics: PhotoImageStatistics,
        afterExposure: (Double) -> Double,
    ): Double {
        val black = afterExposure(statistics.luminancePercentile(BLACK_PERCENTILE))
        val inputBlack = minOf((black - BLACK_MARGIN).coerceAtLeast(0.0) * BLACK_TEMPERING, MAXIMUM_INPUT_BLACK)
        return rounded(inputBlack / PhotoAdjustmentScale.BLACK_POINT_RANGE)
    }

    // 어두운 픽셀이 많을수록 그림자를 더 밝힌다. 하이라이트는 반대로 밝은 픽셀이 많을수록 누른다.
    private fun shadows(
        statistics: PhotoImageStatistics,
        afterBlackPoint: (Double) -> Double,
    ): Double {
        val darkFraction = statistics.fraction { afterBlackPoint(it) < DARK_LUMINANCE }
        return rounded(MAXIMUM_SHADOW_LIFT * response(darkFraction / FULL_SHADOW_FRACTION))
    }

    private fun highlights(
        statistics: PhotoImageStatistics,
        afterBlackPoint: (Double) -> Double,
    ): Double {
        val brightFraction = statistics.fraction { afterBlackPoint(it) > BRIGHT_LUMINANCE }
        return rounded(-MAXIMUM_HIGHLIGHT_RECOVERY * response(brightFraction / FULL_HIGHLIGHT_FRACTION))
    }

    // 중간 밝기 50%가 좁은 범위에 몰려 있으면(밋밋한 사진) 대비를 올린다. 대비를 낮추지는 않는다.
    private fun contrast(
        statistics: PhotoImageStatistics,
        afterBlackPoint: (Double) -> Double,
    ): Double {
        val midSpread = afterBlackPoint(statistics.luminancePercentile(UPPER_QUARTILE)) -
            afterBlackPoint(statistics.luminancePercentile(LOWER_QUARTILE))
        return rounded(((TARGET_MID_SPREAD - midSpread) * CONTRAST_SLOPE).coerceIn(0.0, MAXIMUM_CONTRAST))
    }

    // 흑백에 가까운 사진은 두고, 색이 덜 선명한 사진만 채도를 조금 올린다.
    private fun saturation(statistics: PhotoImageStatistics): Double {
        val saturation = statistics.saturation90
        if (saturation <= GRAYSCALE_SATURATION) return 0.0
        return rounded(((TARGET_SATURATION - saturation) * SATURATION_SLOPE).coerceIn(0.0, MAXIMUM_SATURATION))
    }

    // 비율에 0.7제곱을 해서, 어두운·밝은 영역이 좁아도(예: 10%) 값이 너무 작아지지 않게 한다.
    private fun response(ratio: Double): Double = ratio.coerceIn(0.0, 1.0).pow(RESPONSE_EXPONENT)

    // 소수 둘째 자리(화면의 정수 값)로 반올림한다. 0.5는 0에서 먼 쪽으로 올리고 -0은 0으로 바꾼다.
    private fun rounded(value: Double): Double =
        sign(value) * floor(abs(value) * ROUNDING_SCALE + HALF) / ROUNDING_SCALE + 0.0
}
