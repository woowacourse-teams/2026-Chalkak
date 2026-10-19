package com.stonefive.chalkak.domain.model

/** 조정 항목. 선언 순서가 화면 순서다. iOS `PhotoEditAdjustment`와 같은 항목이다. */
enum class PhotoAdjustment {
    Exposure,
    Contrast,
    Highlights,
    Shadows,
    BlackPoint,
    Saturation,
    Warmth,
}

/**
 * 항목별 조정 값. 화면에서 고르는 값은 -1..1이고 0이면 바꾸지 않는다.
 *
 * iOS와 같은 결과를 내도록 계산식은 [PhotoToneCurve]와 [PhotoColorTransform]에 플랫폼 API에 기대지 않는 형태로 둔다.
 */
data class PhotoAdjustments(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val blackPoint: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
) {
    val isIdentity: Boolean get() = this == PhotoAdjustments()

    operator fun get(adjustment: PhotoAdjustment): Float = when (adjustment) {
        PhotoAdjustment.Exposure -> exposure
        PhotoAdjustment.Contrast -> contrast
        PhotoAdjustment.Highlights -> highlights
        PhotoAdjustment.Shadows -> shadows
        PhotoAdjustment.BlackPoint -> blackPoint
        PhotoAdjustment.Saturation -> saturation
        PhotoAdjustment.Warmth -> warmth
    }

    // -0은 0과 같은 값이지만 data class 비교에서는 다르므로 0으로 바꿔 둔다.
    fun with(
        adjustment: PhotoAdjustment,
        value: Float,
    ): PhotoAdjustments = when (adjustment) {
        PhotoAdjustment.Exposure -> copy(exposure = value + 0f)
        PhotoAdjustment.Contrast -> copy(contrast = value + 0f)
        PhotoAdjustment.Highlights -> copy(highlights = value + 0f)
        PhotoAdjustment.Shadows -> copy(shadows = value + 0f)
        PhotoAdjustment.BlackPoint -> copy(blackPoint = value + 0f)
        PhotoAdjustment.Saturation -> copy(saturation = value + 0f)
        PhotoAdjustment.Warmth -> copy(warmth = value + 0f)
    }

    /** `this × scale + offset`을 항목마다 -1..1로 자른다. */
    fun scaled(
        scale: Float,
        offset: PhotoAdjustments,
    ): PhotoAdjustments = of { adjustment -> (this[adjustment] * scale + offset[adjustment]).coerceIn(-1f, 1f) }

    companion object {
        fun of(valueOf: (PhotoAdjustment) -> Float): PhotoAdjustments =
            PhotoAdjustment.entries.fold(PhotoAdjustments()) { result, adjustment ->
                result.with(adjustment, valueOf(adjustment))
            }
    }
}
