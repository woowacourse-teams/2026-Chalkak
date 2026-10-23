package com.stonefive.chalkak.domain.model.photoedit

/**
 * 조정 값이 어디서 왔는지 나눠 보관한다. 항목마다 사용자가 직접 만졌으면 그 값을, 아니면 `자동 보정값 × 강도`를 적용한다.
 *
 * 예: 자동 보정이 노출 0.4·대비 0.2를 정했고 강도가 1이다. 사용자가 노출을 0.3으로 내리면
 * - 자동 보정을 끄면: 노출 0.3(직접 만진 값 그대로), 대비 0
 * - 강도를 0.5로 낮추면: 노출 0.3(그대로), 대비 0.1
 * 즉 자동 보정을 끄거나 강도를 바꿔도 사용자가 직접 만진 항목은 화면에서 본 값 그대로 남는다.
 */
data class PhotoAdjustmentLayers(
    /** 자른 사진을 분석해 [PhotoAutoAdjuster]가 정한 값. 분석 전이면 null이다. */
    val automatic: PhotoAdjustments? = null,
    /** 자동 보정을 얼마나 적용할지(0..1). 0이면 자동 보정이 꺼진 상태다. */
    val intensity: Float = 0f,
    /** 사용자가 직접 정한 항목별 값(-1..1). 여기 없는 항목은 자동 보정을 따른다. */
    val manual: Map<PhotoAdjustment, Float> = emptyMap(),
) {
    val applied: PhotoAdjustments
        get() = PhotoAdjustments.of { adjustment -> manual[adjustment] ?: automaticShare(adjustment) }

    /** 자동 보정이 각 항목에 정한 몫(`automatic × intensity`). 꺼져 있거나 분석 전이면 null이다. */
    val appliedAutomatic: PhotoAdjustments?
        get() = automatic
            ?.takeIf { intensity > 0f }
            ?.scaled(scale = intensity, offset = PhotoAdjustments())

    /** 조정 초기화 버튼을 켤지. 분석값만 있고 꺼져 있으면 사진이 바뀌지 않았으므로 false다. */
    val isAdjusted: Boolean get() = intensity > 0f || manual.isNotEmpty()

    fun withIntensity(value: Float): PhotoAdjustmentLayers = copy(intensity = value)

    /**
     * 사용자가 눈금자로 [adjustment]를 [value]로 맞췄다.
     * 자동 보정 몫과 같은 값(눈금자가 멈칫하는 자리)으로 되돌리면 직접 만진 표시를 지워, 그 항목은 다시 자동 보정을 따른다.
     * 자동 보정이 꺼져 있으면 자동 몫은 0이므로 0으로 되돌리면 지워진다.
     */
    fun withValue(
        adjustment: PhotoAdjustment,
        value: Float,
    ): PhotoAdjustmentLayers = if (value == automaticShare(adjustment)) {
        copy(manual = manual - adjustment)
    } else {
        // `+ 0f`는 -0을 0으로 바꿔 data class 비교가 틀어지지 않게 한다.
        copy(manual = manual + (adjustment to value + 0f))
    }

    fun withAutomatic(automatic: PhotoAdjustments): PhotoAdjustmentLayers = copy(automatic = automatic)

    /** 조정 초기화. 분석값은 남겨 두어 다시 켤 때 분석을 기다리지 않게 한다. */
    fun cleared(): PhotoAdjustmentLayers = copy(intensity = 0f, manual = emptyMap())

    private fun automaticShare(adjustment: PhotoAdjustment): Float = appliedAutomatic?.get(adjustment) ?: 0f

    companion object {
        /** 자동 보정 없이 [adjustments]를 모두 직접 정한 값으로 둔다. 0인 항목은 넣지 않는다. */
        fun manualOnly(adjustments: PhotoAdjustments): PhotoAdjustmentLayers = PhotoAdjustmentLayers(
            manual = PhotoAdjustment.entries
                .filter { adjustments[it] != 0f }
                .associateWith { adjustments[it] },
        )
    }
}
