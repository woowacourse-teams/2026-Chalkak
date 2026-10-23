package com.stonefive.chalkak.domain.model.photoedit

import kotlin.math.pow

/**
 * 조정 값(-1..1)이 사진을 실제로 얼마나 바꾸는지 정한다. 예: 노출 0.5 → 0.75EV → 빛 약 1.68배.
 * 색 변환([PhotoColorTransform], [PhotoToneCurve])과 자동 보정([PhotoAutoAdjuster])이 같은 식을 써야
 * 자동 보정이 예상한 결과와 실제 결과가 같다.
 */
object PhotoAdjustmentScale {
    /** 노출 1.0이 올리는 밝기(EV). 1EV는 빛 2배다. */
    const val EXPOSURE_RANGE_EV = 1.5

    /** 블랙 포인트 1.0이면 밝기 0.3 아래를 검게 만든다. -1.0이면 검은색을 밝기 0.3까지 띄운다. */
    const val BLACK_POINT_RANGE = 0.3

    /** 따뜻함 1.0이면 빨강 빛을 25% 늘리고 파랑 빛을 25% 줄인다(선형 값 기준). */
    const val WARMTH_GAIN = 0.25

    /** 선형 값(빛의 양)에 곱할 배율. 0이면 1이다. */
    fun exposureGain(exposure: Double): Double = 2.0.pow(exposure * EXPOSURE_RANGE_EV)

    fun inputBlack(blackPoint: Double): Double = blackPoint.coerceAtLeast(0.0) * BLACK_POINT_RANGE

    fun outputBlack(blackPoint: Double): Double = (-blackPoint).coerceAtLeast(0.0) * BLACK_POINT_RANGE

    /** 밝기 [value]에서 [inputBlack] 아래는 0으로 보내고, 나머지를 0..1로 다시 편다. 예: inputBlack 0.2면 0.6 → 0.5. */
    fun stretchAboveBlack(
        value: Double,
        inputBlack: Double,
    ): Double = ((value - inputBlack) / (1 - inputBlack)).coerceIn(0.0, 1.0)
}
