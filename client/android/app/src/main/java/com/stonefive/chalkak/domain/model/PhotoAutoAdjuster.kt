package com.stonefive.chalkak.domain.model

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * 자동 보정값을 정한다. iOS `PhotoEditAutoAdjuster`를 상수까지 그대로 옮겼다.
 *
 * Apple 특허(US 8,983,186 B2)와 Core Image 자동 보정의 동작에서 가져온 원칙을 따른다.
 * - 히스토그램 백분위수로 검은 점·흰 점을 잡되, 끝까지 늘리지 않고 덜어 내서(tempering) 과보정을 피한다.
 * - 중간 밝기는 옮기지 않고, 중간 대비는 높이기만 하고 낮추지 않는다.
 * - 그림자 들어 올림은 0.45 정도에서 멈춘다.
 * - 채도가 이미 높은 사진은 건드리지 않고, 파랑·초록을 두 배로 쳐서 피부색 위주 사진이 과하게 진해지지 않게 한다.
 * - 단계마다 앞 단계를 적용한 결과를 보고 다음 값을 정한다(노출 → 블랙 포인트 → 그림자·하이라이트 → 대비).
 * - 문턱 없이 사진 상태에 비례해 값을 정한다. 잘 찍힌 사진도 작은 값이 여러 항목에 채워진다.
 */
object PhotoAutoAdjuster {
    private const val WHITE_PERCENTILE = 0.995
    private const val BLACK_PERCENTILE = 0.005
    private const val MEDIAN = 0.5
    private const val LOWER_QUARTILE = 0.25
    private const val UPPER_QUARTILE = 0.75
    private const val TARGET_WHITE = 0.96
    private const val MINIMUM_WHITE = 0.05
    private const val EXPOSURE_TEMPERING = 0.6
    private const val BRIGHT_MEDIAN = 0.62
    private const val BRIGHT_EXPOSURE_SLOPE = 1.2
    private const val MINIMUM_EXPOSURE_EV = -0.5
    private const val MAXIMUM_EXPOSURE_EV = 1.0

    private const val BLACK_MARGIN = 0.01
    private const val BLACK_TEMPERING = 0.8
    private const val MAXIMUM_INPUT_BLACK = 0.25

    private const val RESPONSE_EXPONENT = 0.7
    private const val DARK_LUMINANCE = 0.25

    /** 어두운 영역이 이만큼이면 그림자를 최대로 들어 올린다. */
    private const val FULL_SHADOW_FRACTION = 0.8
    private const val MAXIMUM_SHADOW_LIFT = 0.45
    private const val BRIGHT_LUMINANCE = 0.85

    /** 밝은 영역이 이만큼이면 하이라이트를 최대로 누른다. */
    private const val FULL_HIGHLIGHT_FRACTION = 0.3
    private const val MAXIMUM_HIGHLIGHT_RECOVERY = 0.5

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
        var adjustments = PhotoAdjustments()
        // 히스토그램 위치를 앞 단계 보정을 적용한 위치로 옮긴다. 보정은 단조 증가라 백분위수 순서가 유지된다.
        var transform: (Double) -> Double = { it }
        fun percentile(fraction: Double) = transform(statistics.luminancePercentile(fraction))

        // 노출: 흰 쪽이 비어 있으면 흰 점이 목표에 가까워지도록 올리고, 전체가 밝으면 조금 내린다.
        val white = percentile(WHITE_PERCENTILE)
        var exposureEv = 0.0
        if (white < TARGET_WHITE) {
            val gain = PhotoToneCurve.linear(TARGET_WHITE) / PhotoToneCurve.linear(max(white, MINIMUM_WHITE))
            exposureEv = ln(gain) / ln(2.0) * EXPOSURE_TEMPERING
        } else if (percentile(MEDIAN) > BRIGHT_MEDIAN) {
            exposureEv = -(percentile(MEDIAN) - BRIGHT_MEDIAN) * BRIGHT_EXPOSURE_SLOPE
        }
        exposureEv = exposureEv.coerceIn(MINIMUM_EXPOSURE_EV, MAXIMUM_EXPOSURE_EV)
        val exposure = rounded(exposureEv / PhotoToneCurve.EXPOSURE_RANGE_EV)
        adjustments = adjustments.copy(exposure = exposure.toFloat())
        val exposed = transform
        val exposureGain = 2.0.pow(exposure * PhotoToneCurve.EXPOSURE_RANGE_EV)
        transform = { PhotoToneCurve.gamma(minOf(1.0, PhotoToneCurve.linear(exposed(it)) * exposureGain)) }

        // 블랙 포인트: 가장 어두운 쪽이 떠 있는 만큼 일부만 끌어내린다.
        val black = percentile(BLACK_PERCENTILE)
        val inputBlackTarget = minOf((black - BLACK_MARGIN).coerceAtLeast(0.0) * BLACK_TEMPERING, MAXIMUM_INPUT_BLACK)
        val blackPoint = rounded(inputBlackTarget / PhotoToneCurve.BLACK_POINT_RANGE)
        adjustments = adjustments.copy(blackPoint = blackPoint.toFloat())
        val leveled = transform
        val inputBlack = blackPoint.coerceAtLeast(0.0) * PhotoToneCurve.BLACK_POINT_RANGE
        transform = { (leveled(it) - inputBlack).coerceAtLeast(0.0) / (1 - inputBlack) }

        // 그림자·하이라이트: 어두운 영역·밝은 영역이 넓을수록 살린다. 좁은 영역에도 반응하도록 1보다 작은 지수로 올린다.
        val darkFraction = statistics.fraction { transform(it) < DARK_LUMINANCE }
        val brightFraction = statistics.fraction { transform(it) > BRIGHT_LUMINANCE }
        adjustments = adjustments.copy(
            shadows = rounded(MAXIMUM_SHADOW_LIFT * response(darkFraction / FULL_SHADOW_FRACTION)).toFloat(),
            highlights = rounded(
                -MAXIMUM_HIGHLIGHT_RECOVERY * response(brightFraction / FULL_HIGHLIGHT_FRACTION),
            ).toFloat(),
        )

        // 대비: 중간 50%가 좁게 몰려 있으면 넓힌다. 낮추지는 않는다.
        val midSpread = percentile(UPPER_QUARTILE) - percentile(LOWER_QUARTILE)
        adjustments = adjustments.copy(
            contrast = rounded(((TARGET_MID_SPREAD - midSpread) * CONTRAST_SLOPE).coerceIn(0.0, MAXIMUM_CONTRAST))
                .toFloat(),
        )

        // 채도: 흑백에 가까운 사진은 그대로 두고, 덜 선명한 사진만 조금 올린다.
        val saturation = statistics.saturation90
        if (saturation > GRAYSCALE_SATURATION) {
            val value = ((TARGET_SATURATION - saturation) * SATURATION_SLOPE).coerceIn(0.0, MAXIMUM_SATURATION)
            adjustments = adjustments.copy(saturation = rounded(value).toFloat())
        }
        return adjustments
    }

    private fun response(ratio: Double): Double = ratio.coerceIn(0.0, 1.0).pow(RESPONSE_EXPONENT)

    // Swift `rounded()`처럼 0.5는 0에서 먼 쪽으로 반올림한다. -0은 0으로 바꾼다.
    private fun rounded(value: Double): Double =
        sign(value) * floor(abs(value) * ROUNDING_SCALE + HALF) / ROUNDING_SCALE + 0.0
}

/** 자동 보정에 쓰는 밝기 히스토그램과 채도 통계. iOS `PhotoEditImageStatistics`와 같은 방식으로 센다. */
class PhotoImageStatistics(
    /** 0..255 밝기(sRGB 감마 공간의 Rec. 709 가중치)의 픽셀 비율. */
    val luminanceHistogram: DoubleArray,
    /** 파랑·초록이 가장 큰 픽셀의 채도를 두 배로 친 채도 분포의 90번째 백분위수. */
    val saturation90: Double,
) {
    fun luminancePercentile(fraction: Double): Double = percentile(fraction, luminanceHistogram)

    /** 밝기가 조건을 만족하는 픽셀의 비율. */
    fun fraction(predicate: (Double) -> Boolean): Double =
        luminanceHistogram.withIndex().sumOf { (index, value) -> if (predicate(index / MAX_LEVEL)) value else 0.0 }

    companion object {
        /** 자동 보정은 긴 변을 이만큼 줄인 사진으로 센다. */
        const val SAMPLE_LONG_EDGE = 256

        private const val LEVELS = 256
        private const val MAX_LEVEL = 255.0
        private const val SATURATION_PERCENTILE = 0.9
        private const val LUMA_RED = 0.2126
        private const val LUMA_GREEN = 0.7152
        private const val LUMA_BLUE = 0.0722
        private const val CHANNEL_MASK = 0xFF
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8

        fun of(pixels: PhotoPixels): PhotoImageStatistics {
            val histogram = DoubleArray(LEVELS)
            val saturationHistogram = DoubleArray(LEVELS)
            for (color in pixels.argb) {
                val red = (color shr RED_SHIFT and CHANNEL_MASK) / MAX_LEVEL
                val green = (color shr GREEN_SHIFT and CHANNEL_MASK) / MAX_LEVEL
                val blue = (color and CHANNEL_MASK) / MAX_LEVEL
                val luminance = LUMA_RED * red + LUMA_GREEN * green + LUMA_BLUE * blue
                histogram[level(luminance)] += 1.0

                val maximum = maxOf(red, green, blue)
                var saturation = maximum - minOf(red, green, blue)
                if (maximum != red) {
                    saturation = minOf(1.0, saturation * 2)
                }
                saturationHistogram[level(saturation)] += 1.0
            }
            val count = pixels.argb.size
                .toDouble()
            return PhotoImageStatistics(
                luminanceHistogram = DoubleArray(LEVELS) { histogram[it] / count },
                saturation90 = percentile(
                    SATURATION_PERCENTILE,
                    DoubleArray(LEVELS) { saturationHistogram[it] / count },
                ),
            )
        }

        private fun level(value: Double): Int = minOf(LEVELS - 1, (value * MAX_LEVEL).roundToInt())

        private fun percentile(
            fraction: Double,
            histogram: DoubleArray,
        ): Double {
            var cumulative = 0.0
            histogram.forEachIndexed { index, value ->
                cumulative += value
                if (cumulative >= fraction) return index / MAX_LEVEL
            }
            return 1.0
        }
    }
}
