package com.stonefive.chalkak.domain.model

import kotlin.math.abs

/** 조정 항목. 선언 순서가 화면 순서다. */
enum class PhotoAdjustment {
    Brightness,
    Contrast,
    WhitePoint,
    BlackPoint,
    Saturation,
    Warmth,
    Tint,
    Vignette,
}

/**
 * 밝기·대비 같은 조정 값. 모두 -1..1이고 0이면 바꾸지 않는다.
 *
 * 비네트를 뺀 항목은 하나의 4×5 색 행렬로 합쳐 필터와 같은 방식으로 적용한다.
 * 비네트는 결과 이미지 위에 원형 그라데이션을 덧그린다. iOS도 같은 계수를 써야 결과가 같아진다.
 */
data class PhotoAdjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val whitePoint: Float = 0f,
    val blackPoint: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
    val vignette: Float = 0f,
) {
    val isIdentity: Boolean get() = this == PhotoAdjustments()

    /** 양수면 가장자리를 검게, 음수면 희게 덧그린다. 가장자리의 불투명도다. */
    val vignetteAlpha: Float get() = abs(vignette) * MAX_VIGNETTE_ALPHA

    operator fun get(adjustment: PhotoAdjustment): Float = when (adjustment) {
        PhotoAdjustment.Brightness -> brightness
        PhotoAdjustment.Contrast -> contrast
        PhotoAdjustment.WhitePoint -> whitePoint
        PhotoAdjustment.BlackPoint -> blackPoint
        PhotoAdjustment.Saturation -> saturation
        PhotoAdjustment.Warmth -> warmth
        PhotoAdjustment.Tint -> tint
        PhotoAdjustment.Vignette -> vignette
    }

    fun with(
        adjustment: PhotoAdjustment,
        value: Float,
    ): PhotoAdjustments {
        val clamped = value.coerceIn(-1f, 1f)
        return when (adjustment) {
            PhotoAdjustment.Brightness -> copy(brightness = clamped)
            PhotoAdjustment.Contrast -> copy(contrast = clamped)
            PhotoAdjustment.WhitePoint -> copy(whitePoint = clamped)
            PhotoAdjustment.BlackPoint -> copy(blackPoint = clamped)
            PhotoAdjustment.Saturation -> copy(saturation = clamped)
            PhotoAdjustment.Warmth -> copy(warmth = clamped)
            PhotoAdjustment.Tint -> copy(tint = clamped)
            PhotoAdjustment.Vignette -> copy(vignette = clamped)
        }
    }

    /** 비네트를 뺀 조정을 합친 색 행렬. 바꿀 것이 없으면 `null`이다. */
    fun colorMatrix(): FloatArray? {
        val steps = listOfNotNull(
            brightness.takeIf { it != 0f }?.let(::brightnessMatrix),
            contrast.takeIf { it != 0f }?.let(::contrastMatrix),
            whitePoint.takeIf { it != 0f }?.let(::whitePointMatrix),
            blackPoint.takeIf { it != 0f }?.let(::blackPointMatrix),
            saturation.takeIf { it != 0f }?.let(::saturationMatrix),
            warmth.takeIf { it != 0f }?.let(::warmthMatrix),
            tint.takeIf { it != 0f }?.let(::tintMatrix),
        )
        return steps.reduceOrNull { applied, next -> ColorMatrices.concat(next, applied) }
    }

    companion object {
        /** 비네트가 투명에서 시작하는 위치. 결과 이미지 중심에서 꼭짓점까지 거리에 대한 비율이다. */
        const val VIGNETTE_INNER_RADIUS = 0.45f
        const val MAX_VIGNETTE_ALPHA = 0.7f

        private const val MAX_CHANNEL = 255f
        private const val MID_CHANNEL = 128f
        private const val BRIGHTNESS_RANGE = 0.25f
        private const val CONTRAST_RANGE = 0.5f
        private const val LEVEL_RANGE = 0.25f
        private const val TEMPERATURE_RANGE = 0.12f
        private const val LUMA_RED = 0.299f
        private const val LUMA_GREEN = 0.587f
        private const val LUMA_BLUE = 0.114f

        // 모든 채널에 같은 값을 더한다.
        private fun brightnessMatrix(value: Float) =
            ColorMatrices.scale(offset = value * BRIGHTNESS_RANGE * MAX_CHANNEL)

        // 중간 회색(128)을 고정하고 기울기를 바꾼다.
        private fun contrastMatrix(value: Float): FloatArray {
            val scale = 1f + value * CONTRAST_RANGE
            return ColorMatrices.scale(scale = scale, offset = MID_CHANNEL * (1f - scale))
        }

        // 검정을 고정하고 밝은 쪽 끝을 옮긴다.
        private fun whitePointMatrix(value: Float) = ColorMatrices.scale(scale = 1f + value * LEVEL_RANGE)

        // 흰색을 고정하고 어두운 쪽 끝을 옮긴다. 양수면 어두운 부분이 더 어두워진다.
        private fun blackPointMatrix(value: Float): FloatArray {
            val scale = 1f + value * LEVEL_RANGE
            return ColorMatrices.scale(scale = scale, offset = MAX_CHANNEL * (1f - scale))
        }

        // 휘도를 유지하며 회색과 원래 색 사이를 보간한다. -1이면 흑백이다.
        private fun saturationMatrix(value: Float): FloatArray {
            val amount = 1f + value
            val rest = 1f - amount
            return floatArrayOf(
                LUMA_RED * rest + amount, LUMA_GREEN * rest, LUMA_BLUE * rest, 0f, 0f,
                LUMA_RED * rest, LUMA_GREEN * rest + amount, LUMA_BLUE * rest, 0f, 0f,
                LUMA_RED * rest, LUMA_GREEN * rest, LUMA_BLUE * rest + amount, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        }

        // 양수면 빨강을 올리고 파랑을 내린다.
        private fun warmthMatrix(value: Float) = ColorMatrices.channels(
            red = 1f + value * TEMPERATURE_RANGE,
            green = 1f,
            blue = 1f - value * TEMPERATURE_RANGE,
        )

        // 양수면 초록을 내려 자홍색 쪽으로, 음수면 초록 쪽으로 옮긴다.
        private fun tintMatrix(value: Float) = ColorMatrices.channels(
            red = 1f,
            green = 1f - value * TEMPERATURE_RANGE,
            blue = 1f,
        )
    }
}

/** 4×5 행 우선 색 행렬 계산. 마지막 열은 0..255 범위의 더하는 값이다. */
object ColorMatrices {
    private const val ROWS = 4
    private const val COLUMNS = 5
    private const val OFFSET_COLUMN = 4

    fun scale(
        scale: Float = 1f,
        offset: Float = 0f,
    ): FloatArray = floatArrayOf(
        scale, 0f, 0f, 0f, offset,
        0f, scale, 0f, 0f, offset,
        0f, 0f, scale, 0f, offset,
        0f, 0f, 0f, 1f, 0f,
    )

    fun channels(
        red: Float,
        green: Float,
        blue: Float,
    ): FloatArray = floatArrayOf(
        red, 0f, 0f, 0f, 0f,
        0f, green, 0f, 0f, 0f,
        0f, 0f, blue, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** [first]를 적용한 뒤 [second]를 적용하는 행렬. */
    fun concat(
        second: FloatArray,
        first: FloatArray,
    ): FloatArray {
        val result = FloatArray(ROWS * COLUMNS)
        for (row in 0 until ROWS) {
            for (column in 0 until COLUMNS) {
                var value = if (column == OFFSET_COLUMN) second[row * COLUMNS + OFFSET_COLUMN] else 0f
                for (k in 0 until ROWS) {
                    value += second[row * COLUMNS + k] * first[k * COLUMNS + column]
                }
                result[row * COLUMNS + column] = value
            }
        }
        return result
    }
}
