package com.stonefive.chalkak.domain.model.photoedit

import kotlin.math.pow

/**
 * 사진 파일에 저장된 색 값(감마 값)과 빛의 양에 비례하는 값(선형 값) 사이 변환. 둘 다 0..1이다.
 *
 * 저장 값은 사람 눈에 맞춰 어두운 쪽을 촘촘하게 둔 값이라, 저장 값 0.5는 빛의 양으로는 약 0.21이다.
 * 노출처럼 "빛을 몇 배로" 바꾸는 계산은 선형 값에서 해야 자연스럽다. 식은 sRGB 표준을 따른다.
 */
object SrgbTransfer {
    fun linear(value: Double): Double = if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)

    fun gamma(value: Double): Double = if (value <= 0.0031308) value * 12.92 else 1.055 * value.pow(1 / 2.4) - 0.055
}
