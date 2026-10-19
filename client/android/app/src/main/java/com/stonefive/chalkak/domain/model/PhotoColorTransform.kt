package com.stonefive.chalkak.domain.model

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 조정과 필터를 ARGB 픽셀에 적용한다. 미리보기와 업로드 인코더가 같은 계산을 쓴다.
 *
 * 순서는 iOS `PhotoEditFilterRenderer`와 같다. 노출·따뜻함·채도는 선형 공간에서 계산하고, 톤 곡선은 sRGB 감마 공간에서
 * 채널마다 적용한다. [filterMatrix]가 있으면 마지막에 0..255 값에 곱한다.
 */
class PhotoColorTransform(
    adjustments: PhotoAdjustments,
    private val filterMatrix: FloatArray? = null,
) {
    val isIdentity: Boolean = adjustments.isIdentity && filterMatrix == null

    private val saturation = 1f + adjustments.saturation
    private val redGain: Float
    private val greenGain: Float
    private val blueGain: Float

    /** 0..1 선형 값을 [ENCODE_STEPS]개로 나눠 감마 인코딩과 톤 곡선을 거친 0..255 값. */
    private val encode: IntArray

    /** 채도를 바꾸지 않을 때 채널마다 입력 0..255를 결과 0..255로 바로 바꾸는 표. */
    private val redTable: IntArray?
    private val greenTable: IntArray?
    private val blueTable: IntArray?

    init {
        val exposureGain = 2.0
            .pow(adjustments.exposure * PhotoToneCurve.EXPOSURE_RANGE_EV)
            .toFloat()
        val warmth = adjustments.warmth * PhotoToneCurve.WARMTH_GAIN.toFloat()
        redGain = exposureGain * (1f + warmth)
        greenGain = exposureGain
        blueGain = exposureGain * (1f - warmth)

        val curve = if (PhotoToneCurve.isIdentity(adjustments)) null else PhotoToneCurve.samples(adjustments)
        encode = IntArray(ENCODE_STEPS + 1) { step ->
            val gamma = PhotoToneCurve.gamma(step.toDouble() / ENCODE_STEPS)
            val toned = curve?.let { toneAt(it, gamma) } ?: gamma
            (toned * MAX_LEVEL).roundToInt().coerceIn(0, MAX_LEVEL)
        }
        if (saturation == 1f) {
            redTable = IntArray(LEVELS) { encodeLinear(LINEAR[it] * redGain) }
            greenTable = IntArray(LEVELS) { encodeLinear(LINEAR[it] * greenGain) }
            blueTable = IntArray(LEVELS) { encodeLinear(LINEAR[it] * blueGain) }
        } else {
            redTable = null
            greenTable = null
            blueTable = null
        }
    }

    /** [pixels]의 [offset]부터 [length]개를 그 자리에서 바꾼다. 알파는 유지한다. */
    fun apply(
        pixels: IntArray,
        offset: Int = 0,
        length: Int = pixels.size - offset,
    ) {
        if (isIdentity) return
        for (index in offset until offset + length) {
            val color = pixels[index]
            val alpha = color ushr ALPHA_SHIFT
            var red = color shr RED_SHIFT and CHANNEL_MASK
            var green = color shr GREEN_SHIFT and CHANNEL_MASK
            var blue = color and CHANNEL_MASK
            if (redTable != null && greenTable != null && blueTable != null) {
                red = redTable[red]
                green = greenTable[green]
                blue = blueTable[blue]
            } else {
                val linearRed = LINEAR[red] * redGain
                val linearGreen = LINEAR[green] * greenGain
                val linearBlue = LINEAR[blue] * blueGain
                val luma = LUMA_RED * linearRed + LUMA_GREEN * linearGreen + LUMA_BLUE * linearBlue
                red = encodeLinear(luma + saturation * (linearRed - luma))
                green = encodeLinear(luma + saturation * (linearGreen - luma))
                blue = encodeLinear(luma + saturation * (linearBlue - luma))
            }
            pixels[index] = if (filterMatrix == null) {
                (alpha shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
            } else {
                applyFilter(filterMatrix, alpha, red, green, blue)
            }
        }
    }

    private fun encodeLinear(value: Float): Int = encode[(value.coerceIn(0f, 1f) * ENCODE_STEPS).roundToInt()]

    companion object {
        private const val LEVELS = 256
        private const val MAX_LEVEL = 255
        private const val ENCODE_STEPS = 4096
        private const val CHANNEL_MASK = 0xFF
        private const val ALPHA_SHIFT = 24
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8

        // Core Image `CIColorControls`가 채도에 쓰는 선형 공간 휘도 가중치.
        private const val LUMA_RED = 0.2125f
        private const val LUMA_GREEN = 0.7154f
        private const val LUMA_BLUE = 0.0721f

        private const val MATRIX_COLUMNS = 5
        private const val ALPHA_COLUMN = 3
        private const val OFFSET_COLUMN = 4

        private val LINEAR = FloatArray(LEVELS) { PhotoToneCurve.linear(it.toDouble() / MAX_LEVEL).toFloat() }

        /** iOS `CIColorCurves`처럼 이웃한 표본 사이를 직선으로 잇는다. */
        private fun toneAt(
            samples: DoubleArray,
            value: Double,
        ): Double {
            val position = value.coerceIn(0.0, 1.0) * (samples.size - 1)
            val lower = position.toInt().coerceAtMost(samples.size - 2)
            val fraction = position - lower
            return samples[lower] + (samples[lower + 1] - samples[lower]) * fraction
        }

        /** Android `ColorMatrix`와 같은 4×5 행 우선 행렬. 마지막 열은 0..255 범위의 더하는 값이다. */
        private fun applyFilter(
            matrix: FloatArray,
            alpha: Int,
            red: Int,
            green: Int,
            blue: Int,
        ): Int {
            fun row(index: Int): Int {
                val base = index * MATRIX_COLUMNS
                val value = matrix[base] * red +
                    matrix[base + 1] * green +
                    matrix[base + 2] * blue +
                    matrix[base + ALPHA_COLUMN] * alpha +
                    matrix[base + OFFSET_COLUMN]
                return value.roundToInt().coerceIn(0, MAX_LEVEL)
            }
            return (row(3) shl ALPHA_SHIFT) or (row(0) shl RED_SHIFT) or (row(1) shl GREEN_SHIFT) or row(2)
        }
    }
}
