package com.stonefive.chalkak.domain.model.photoedit

/** 조정 항목. 선언 순서대로 화면에 나온다. */
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
 * 항목별 조정 값. 모두 -1..1이고 0이면 그 항목은 사진을 바꾸지 않는다. 화면에는 100을 곱해 -100..100으로 보인다.
 * 각 값이 실제로 얼마나 바꾸는지는 [PhotoAdjustmentScale] 참고.
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

    // `+ 0f`는 -0을 0으로 바꾼다. -0은 0과 같은 값이지만 data class 비교에서는 달라서 isIdentity가 틀어진다.
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

    /** 항목마다 `이 값 × scale + offset`을 계산해 -1..1로 자른다. 자동 보정 × 강도 + 사용자 값을 만들 때 쓴다. */
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
