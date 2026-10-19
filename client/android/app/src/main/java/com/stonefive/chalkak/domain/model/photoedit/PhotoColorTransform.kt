package com.stonefive.chalkak.domain.model.photoedit

import kotlin.math.roundToInt

/**
 * 조정(과 필터)을 픽셀 색에 적용한다. 편집 화면 미리보기와 업로드 인코더가 조정을 이 클래스 하나로 계산해 색이 같다.
 *
 * 필터는 경우에 따라 다른 곳에서 입힌다. 미리보기는 늘 [filterMatrix] 없이 만들고 필터는 그릴 때 Compose 색 필터로 입힌다.
 * 업로드 인코더는 조정이 있으면 [of]로 필터까지 여기서 적용하고, 조정이 없으면 이 클래스를 쓰지 않고 그릴 때 입힌다.
 * 어느 쪽이든 같은 [PhotoFilter] 행렬이라 색은 같다.
 *
 * 픽셀마다 이 순서로 바꾼다.
 * 1. 노출·따뜻함·채도: 빛의 양에 비례하는 값(선형 값)으로 바꿔서 계산한다. 노출 +1EV가 "빛 2배"가 되려면 이 값이어야 한다.
 * 2. 톤 곡선: 다시 0..255 저장 값(감마 값, 눈에 보이는 밝기에 가까움)으로 돌린 뒤 [PhotoToneCurve] 표로 바꾼다.
 * 3. 필터: [filterMatrix]가 있으면 마지막에 0..255 값에 곱한다.
 * 선형 값·감마 값 변환은 [SrgbTransfer] 참고.
 *
 * 만들 때 표를 미리 계산해 두므로, 값이 바뀔 때마다 새로 만들고 여러 픽셀에 [apply]한다.
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

    /** 선형 값(0..1)을 [ENCODE_STEPS]칸으로 나눠, 칸마다 감마 값으로 돌리고 톤 곡선까지 적용한 0..255 결과. */
    private val encode: IntArray

    /**
     * 채도를 바꾸지 않으면 R·G·B가 서로 섞이지 않으므로, 채널마다 입력 0..255 → 결과 0..255 표 하나로 끝난다.
     * 채도를 바꾸면 세 채널을 같이 봐야 해서 null이고 픽셀마다 계산한다.
     */
    private val redTable: IntArray?
    private val greenTable: IntArray?
    private val blueTable: IntArray?

    init {
        val exposureGain = PhotoAdjustmentScale.exposureGain(adjustments.exposure.toDouble()).toFloat()
        val warmth = adjustments.warmth * PhotoAdjustmentScale.WARMTH_GAIN.toFloat()
        redGain = exposureGain * (1f + warmth)
        greenGain = exposureGain
        blueGain = exposureGain * (1f - warmth)

        val curve = if (PhotoToneCurve.isIdentity(adjustments)) null else PhotoToneCurve.lookupTable(adjustments)
        encode = IntArray(ENCODE_STEPS + 1) { step ->
            val gamma = SrgbTransfer.gamma(step.toDouble() / ENCODE_STEPS)
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

    /** [pixels]의 [offset]부터 [length]개를 그 자리에서 바꾼다. 투명도(A)는 유지한다. 큰 사진은 줄 단위로 나눠 넘긴다. */
    fun apply(
        pixels: IntArray,
        offset: Int = 0,
        length: Int = pixels.size - offset,
    ) {
        if (isIdentity) return
        for (index in offset until offset + length) {
            val color = pixels[index]
            val alpha = Argb.alpha(color)
            var red = Argb.red(color)
            var green = Argb.green(color)
            var blue = Argb.blue(color)
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
                Argb.of(alpha, red, green, blue)
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

        // 채도를 바꿀 때 기준이 되는 밝기 = 선형 값 R·G·B에 이 비율을 곱해 더한 값. Apple Core Image의 채도 조정과 같은 값이다.
        // PhotoImageStatistics의 0.2126·0.7152·0.0722와 숫자가 비슷하지만 저장 값에 쓰는 다른 기준이다.
        // 숫자를 맞추면 채도를 바꾼 사진의 결과 색이 달라지고 PhotoColorGoldenTest가 깨진다.
        private const val LUMA_RED = 0.2125f
        private const val LUMA_GREEN = 0.7154f
        private const val LUMA_BLUE = 0.0721f

        private const val MATRIX_COLUMNS = 5
        private const val ALPHA_COLUMN = 3
        private const val OFFSET_COLUMN = 4

        private val LINEAR = FloatArray(LEVELS) { SrgbTransfer.linear(it.toDouble() / MAX_LEVEL).toFloat() }

        fun of(edit: PhotoEdit): PhotoColorTransform = PhotoColorTransform(edit.adjustments, edit.filter.colorMatrix())

        /** 표의 두 칸 사이 값은 직선으로 이어 구한다. */
        private fun toneAt(
            table: DoubleArray,
            value: Double,
        ): Double {
            val position = value.coerceIn(0.0, 1.0) * (table.size - 1)
            val lower = position.toInt().coerceAtMost(table.size - 2)
            val fraction = position - lower
            return table[lower] + (table[lower + 1] - table[lower]) * fraction
        }

        /** [PhotoFilter] 행렬을 곱한다. 행렬 배치는 [PhotoFilter] 참고. */
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
            return Argb.of(row(3), row(0), row(1), row(2))
        }
    }
}
