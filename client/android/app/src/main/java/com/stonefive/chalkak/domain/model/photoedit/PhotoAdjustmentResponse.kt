package com.stonefive.chalkak.domain.model.photoedit

import kotlin.math.pow

/** 조정 항목 값(-1..1)을 실제로 바꾸는 양으로 옮기는 식. 색 변환과 자동 보정이 같은 식을 쓴다. */
object PhotoAdjustmentResponse {
    /** 노출 1.0이 올리는 EV. */
    const val EXPOSURE_RANGE_EV = 1.5

    /** 블랙 포인트 1.0이 검게 만드는 입력 밝기. -1.0이면 같은 만큼 검은색을 띄운다. */
    const val BLACK_POINT_RANGE = 0.3

    /** 따뜻함 1.0이 선형 공간에서 빨강을 올리고 파랑을 내리는 비율. */
    const val WARMTH_GAIN = 0.25

    /** 선형 공간에서 곱하는 배율. */
    fun exposureGain(exposure: Double): Double = 2.0.pow(exposure * EXPOSURE_RANGE_EV)

    fun inputBlack(blackPoint: Double): Double = blackPoint.coerceAtLeast(0.0) * BLACK_POINT_RANGE

    fun outputBlack(blackPoint: Double): Double = (-blackPoint).coerceAtLeast(0.0) * BLACK_POINT_RANGE

    /** [inputBlack] 아래를 검은색으로 보내고 나머지를 0..1로 다시 편다. */
    fun leveled(
        value: Double,
        inputBlack: Double,
    ): Double = ((value - inputBlack) / (1 - inputBlack)).coerceIn(0.0, 1.0)
}
