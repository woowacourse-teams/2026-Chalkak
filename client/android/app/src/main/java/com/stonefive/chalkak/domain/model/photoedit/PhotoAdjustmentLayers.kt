package com.stonefive.chalkak.domain.model.photoedit

/**
 * 조정 값이 어디서 왔는지 나눠 보관한다. 사진에 적용할 값은 `자동 보정값 × 강도 + 사용자 값`이다(항목마다 -1..1로 자름).
 *
 * 합친 값만 저장하면, 잘린 뒤에는 사용자가 정한 값을 되찾을 수 없다. 예: 사용자 노출 1.0 + 자동 0.3 → 1.0으로 잘림.
 * 그래서 세 값을 따로 두고, 화면에 보이는 값([applied])은 그때그때 계산한다.
 */
data class PhotoAdjustmentLayers(
    /** 자른 사진을 분석해 [PhotoAutoAdjuster]가 정한 값. 분석 전이면 null이다. */
    val automatic: PhotoAdjustments? = null,
    /** 자동 보정을 얼마나 적용할지(0..1). 0이면 자동 보정이 꺼진 상태다. */
    val intensity: Float = 0f,
    /** 사용자가 자동 보정 위에 더한 값. */
    val manual: PhotoAdjustments = PhotoAdjustments(),
) {
    val applied: PhotoAdjustments
        get() = (automatic ?: PhotoAdjustments()).scaled(scale = intensity, offset = manual)

    /** 자동 보정이 각 항목에 더한 몫(`automatic × intensity`). 꺼져 있거나 분석 전이면 null이다. */
    val appliedAutomatic: PhotoAdjustments?
        get() = automatic
            ?.takeIf { intensity > 0f }
            ?.scaled(scale = intensity, offset = PhotoAdjustments())

    /** 조정 초기화 버튼을 켤지. 분석값만 있고 꺼져 있으면 사진이 바뀌지 않았으므로 false다. */
    val isAdjusted: Boolean get() = intensity > 0f || !manual.isIdentity

    fun withIntensity(value: Float): PhotoAdjustmentLayers = copy(intensity = value)

    /**
     * 사용자가 눈금자로 고른 값이 그대로 화면에 보이도록 사용자 값을 역산한다.
     * 예: 자동 몫 0.2인 노출을 0.5로 맞추면 manual은 0.3이 된다. 이후 강도를 바꿔도 사용자가 더한 0.3은 유지된다.
     * 자동 몫보다 낮게 맞추면 manual은 음수다. 예: 자동 몫 0.4에서 0.3으로 내리면 manual은 −0.1이고,
     * 이 상태로 자동 보정을 끄면 노출은 원본보다 어두운 −0.1이 된다.
     */
    fun withValue(
        adjustment: PhotoAdjustment,
        value: Float,
    ): PhotoAdjustmentLayers {
        val automaticValue = appliedAutomatic?.get(adjustment) ?: 0f
        return copy(manual = manual.with(adjustment, value - automaticValue))
    }

    fun withAutomatic(automatic: PhotoAdjustments): PhotoAdjustmentLayers = copy(automatic = automatic)

    /** 조정 초기화. 분석값은 남겨 두어 다시 켤 때 분석을 기다리지 않게 한다. */
    fun cleared(): PhotoAdjustmentLayers = copy(intensity = 0f, manual = PhotoAdjustments())
}
