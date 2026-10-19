package com.stonefive.chalkak.domain.model.photoedit

import kotlin.math.roundToInt

/** 자동 보정에 쓰는 밝기 히스토그램과 채도 통계. */
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

        fun of(pixels: PhotoPixels): PhotoImageStatistics {
            val histogram = DoubleArray(LEVELS)
            val saturationHistogram = DoubleArray(LEVELS)
            for (color in pixels.argb) {
                val red = Argb.red(color) / MAX_LEVEL
                val green = Argb.green(color) / MAX_LEVEL
                val blue = Argb.blue(color) / MAX_LEVEL
                val luminance = LUMA_RED * red + LUMA_GREEN * green + LUMA_BLUE * blue
                histogram[level(luminance)] += 1.0

                // 피부색처럼 빨강이 큰 사진이 과하게 진해지지 않도록 파랑·초록 쪽 채도를 더 크게 센다.
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
