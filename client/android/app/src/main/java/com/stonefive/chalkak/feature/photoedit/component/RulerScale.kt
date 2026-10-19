package com.stonefive.chalkak.feature.photoedit.component

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 위치는 값과 같은 단위지만 기준값([anchor]) 앞뒤에 [detentHalfWidth]만큼 멈춤 구간을 끼워 넣는다.
 * 그래서 기준값에 닿은 뒤 그 구간만큼 더 밀어야 값이 다시 바뀐다.
 */
internal class RulerScale(
    val range: ClosedFloatingPointRange<Float>,
    anchor: Float,
    private val detentHalfWidth: Float,
) {
    val anchor: Float = anchor.coerceIn(range)

    /** 범위 양 끝의 위치. */
    val lowerPosition: Float get() = position(range.start)
    val upperPosition: Float get() = position(range.endInclusive)

    fun position(value: Float): Float = when {
        abs(value - anchor) < ANCHOR_TOLERANCE -> anchor
        value > anchor -> value + detentHalfWidth
        else -> value - detentHalfWidth
    }

    /** 위치에 맞는 값. 기준값은 반올림하지 않아야 자동 보정 위에 더한 값이 정확히 0이 된다. */
    fun value(position: Float): Float {
        val clamped = position.coerceIn(lowerPosition, upperPosition)
        val value = when {
            clamped > anchor + detentHalfWidth -> clamped - detentHalfWidth
            clamped < anchor - detentHalfWidth -> clamped + detentHalfWidth
            else -> return anchor
        }
        return snapped(value)
    }

    /** 화면에 보이는 정수 값(×100)에 맞춰 자른다. */
    fun snapped(value: Float): Float = (value.coerceIn(range) * PERCENT).roundToInt() / PERCENT

    fun isAnchor(value: Float): Boolean = abs(value - anchor) < ANCHOR_TOLERANCE

    companion object {
        /** 값 1.0을 50칸(2 단위 눈금)으로 나눈다. */
        const val TICKS_PER_UNIT = 50

        private const val PERCENT = 100f
        private const val ANCHOR_TOLERANCE = 1e-6f

        /** 값이 속한 눈금 칸. 칸이 바뀔 때마다 햅틱을 낸다. */
        fun tickOf(value: Float): Int = floor(value * TICKS_PER_UNIT + ANCHOR_TOLERANCE).toInt()
    }
}
