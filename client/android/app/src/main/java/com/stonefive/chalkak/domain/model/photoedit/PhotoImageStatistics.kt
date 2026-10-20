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
    /** 채도가 낮은 쪽부터 90%째 픽셀의 채도(0..1). 채도 = 가장 큰 채널 − 가장 작은 채널(저장 값 기준). */
    val saturation90: Double,
    /** 피부색으로 보이는 픽셀의 비율(0..1). 피부색 기준은 [isSkinTone] 참고. */
    val skinFraction: Double = 0.0,
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

        private const val SKIN_MINIMUM_VALUE = 0.2
        private const val SKIN_MINIMUM_CHROMA_RATIO = 0.1
        private const val SKIN_MAXIMUM_CHROMA_RATIO = 0.6
        private const val SKIN_MAXIMUM_HUE_DEGREES = 50.0
        private const val HUE_DEGREES_PER_SECTOR = 60.0

        fun of(pixels: PhotoPixels): PhotoImageStatistics {
            val histogram = DoubleArray(LEVELS)
            val saturationHistogram = DoubleArray(LEVELS)
            var skinCount = 0
            for (color in pixels.argb) {
                val red = Argb.red(color) / MAX_LEVEL
                val green = Argb.green(color) / MAX_LEVEL
                val blue = Argb.blue(color) / MAX_LEVEL
                val luminance = LUMA_RED * red + LUMA_GREEN * green + LUMA_BLUE * blue
                histogram[level(luminance)] += 1.0

                saturationHistogram[level(maxOf(red, green, blue) - minOf(red, green, blue))] += 1.0
                if (isSkinTone(red, green, blue)) skinCount++
            }
            val count = pixels.argb.size
                .toDouble()
            return PhotoImageStatistics(
                luminanceHistogram = DoubleArray(LEVELS) { histogram[it] / count },
                saturation90 = percentile(
                    SATURATION_PERCENTILE,
                    DoubleArray(LEVELS) { saturationHistogram[it] / count },
                ),
                skinFraction = skinCount / count,
            )
        }

        /**
         * 피부색으로 볼지. 저장 값 R·G·B(0..1)가 모두 맞아야 한다.
         * - 빨강 ≥ 초록 ≥ 파랑이고, 가장 큰 값(빨강)이 0.2 이상이다(너무 어두운 픽셀 제외).
         * - (빨강 − 파랑) ÷ 빨강이 0.1..0.6이다. 회색에 가깝거나(0.1 미만) 주황처럼 쨍한 색(0.6 초과)은 뺀다.
         * - 색상이 0..50°다. 빨강(0°)에서 노랑(60°) 사이 중 노랑에 가까운 쪽은 뺀다.
         *
         * 예: 밝은 피부 (0.94, 0.78, 0.69)와 어두운 피부 (0.45, 0.30, 0.22)는 피부색, 하늘 (0.53, 0.81, 0.92)과
         * 노을 (1.0, 0.5, 0.1)은 아니다.
         */
        private fun isSkinTone(
            red: Double,
            green: Double,
            blue: Double,
        ): Boolean {
            if (red < green || green < blue || red < SKIN_MINIMUM_VALUE) return false
            val chroma = red - blue
            val chromaRatio = chroma / red
            if (chromaRatio !in SKIN_MINIMUM_CHROMA_RATIO..SKIN_MAXIMUM_CHROMA_RATIO) return false
            val hueDegrees = HUE_DEGREES_PER_SECTOR * (green - blue) / chroma
            return hueDegrees <= SKIN_MAXIMUM_HUE_DEGREES
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
