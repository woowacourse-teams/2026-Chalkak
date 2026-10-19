package com.stonefive.chalkak.domain.model.photoedit

/** 적용할 조정은 `자동 보정값 × 강도 + 수동 값`이다. 수동 값을 따로 두어 다시 편집해도 잃지 않는다. */
data class PhotoAdjustmentLayers(
    /** 자른 사진을 분석한 자동 보정값. 분석 전이면 null이다. */
    val automatic: PhotoAdjustments? = null,
    val intensity: Float = 0f,
    val manual: PhotoAdjustments = PhotoAdjustments(),
) {
    val applied: PhotoAdjustments
        get() = (automatic ?: PhotoAdjustments()).scaled(scale = intensity, offset = manual)

    /** 자동 보정이 각 항목에 더한 값. 꺼져 있거나 분석 전이면 null이다. */
    val appliedAutomatic: PhotoAdjustments?
        get() = automatic
            ?.takeIf { intensity > 0f }
            ?.scaled(scale = intensity, offset = PhotoAdjustments())

    val isAdjusted: Boolean get() = intensity > 0f || !manual.isIdentity

    fun withIntensity(value: Float): PhotoAdjustmentLayers = copy(intensity = value)

    /** 자동 보정 몫은 두고 [adjustment]의 적용 값이 [value]가 되도록 수동 값을 정한다. */
    fun withValue(
        adjustment: PhotoAdjustment,
        value: Float,
    ): PhotoAdjustmentLayers {
        val automaticValue = appliedAutomatic?.get(adjustment) ?: 0f
        return copy(manual = manual.with(adjustment, value - automaticValue))
    }

    fun withAutomatic(automatic: PhotoAdjustments): PhotoAdjustmentLayers = copy(automatic = automatic)

    fun cleared(): PhotoAdjustmentLayers = copy(intensity = 0f, manual = PhotoAdjustments())
}
