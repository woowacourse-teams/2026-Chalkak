package com.stonefive.chalkak.domain.model.photoedit

import kotlin.math.pow

object SrgbTransfer {
    fun linear(value: Double): Double = if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)

    fun gamma(value: Double): Double = if (value <= 0.0031308) value * 12.92 else 1.055 * value.pow(1 / 2.4) - 0.055
}
