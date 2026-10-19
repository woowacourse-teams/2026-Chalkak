package com.stonefive.chalkak.domain.model.photoedit

import kotlin.math.roundToInt

/**
 * 자동 보정이 보는 사진 통계. [of]로 픽셀에서 센다. 통계만 있으면 되므로 테스트에서는 직접 만들 수도 있다.
 */
class PhotoImageStatistics(
    /**
     * 밝기별 픽셀 비율. i번째 값은 밝기가 i(0..255)인 픽셀의 비율이고 전체 합은 1이다.
     * 밝기는 저장 값(감마 값) R·G·B를 0.2126·R + 0.7152·G + 0.0722·B로 합친 값이다.
     */
    val luminanceHistogram: DoubleArray,
    /**
     * 채도가 낮은 쪽부터 90%째 픽셀의 채도(0..1). 채도 = 가장 큰 채널 − 가장 작은 채널이고,
     * 빨강이 가장 크지 않은 픽셀은 이 채도 값을 2배로 한다(최대 1, [of] 참고).
     */
    val saturation90: Double,
) {
    /** 어두운 쪽부터 [fraction]만큼 센 지점의 밝기(0..1). 예: 0.5면 밝기 중앙값. */
    fun luminancePercentile(fraction: Double): Double = percentile(fraction, luminanceHistogram)

    fun fraction(predicate: (Double) -> Boolean): Double =
        luminanceHistogram.withIndex().sumOf { (index, value) -> if (predicate(index / MAX_LEVEL)) value else 0.0 }

    companion object {
        const val SAMPLE_LONG_EDGE = 256

        private const val LEVELS = 256
        private const val MAX_LEVEL = 255.0
        private const val SATURATION_PERCENTILE = 0.9
        private const val LUMA_RED = 0.2126
        private const val LUMA_GREEN = 0.7152
        private const val LUMA_BLUE = 0.0722

        fun of(pixels: PhotoPixels): PhotoImageStatistics {
            val histogram = DoubleArray(LEVELS)
            val saturationHistogram = DoubleArray(LEVELS)
            for (color in pixels.argb) {
                val red = Argb.red(color) / MAX_LEVEL
                val green = Argb.green(color) / MAX_LEVEL
                val blue = Argb.blue(color) / MAX_LEVEL
                val luminance = LUMA_RED * red + LUMA_GREEN * green + LUMA_BLUE * blue
                histogram[level(luminance)] += 1.0

                // 하늘·풀처럼 파랑·초록이 가장 큰 픽셀은 채도 값을 2배로 친다. 그래서 그런 사진은 이미 선명하다고 보고
                // 채도를 덜 올리고, 빨강이 큰 피부색 위주 사진은 상대적으로 덜 선명하게 보여 조금 더 올린다.
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
