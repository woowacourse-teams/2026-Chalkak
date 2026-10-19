package com.stonefive.chalkak.feature.photoedit.component

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 눈금자에서 손가락 위치와 조정 값을 서로 바꾼다.
 *
 * 기준값([anchor]: 이 항목의 자동 보정 몫 `자동 보정값 × 강도`가 있으면 그 값, 없으면 0)을 지나칠 때 살짝 멈칫하도록,
 * 기준값 앞뒤에 값이 바뀌지 않는 "멈춤 구간"을 [detentHalfWidth]씩(합쳐서 두 배) 끼워 넣는다.
 * 위치는 값과 같은 단위지만 기준값을 지난 쪽은 이 구간만큼 밀려 있다.
 * 예: anchor 0, detentHalfWidth 0.02면 위치 0.01 → 값 0, 위치 0.05 → 값 0.03.
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
        /** 값 1.0을 50칸으로 나눈다. 눈금 한 칸은 화면 값(×100)으로 2다. */
        const val TICKS_PER_UNIT = 50

        private const val PERCENT = 100f
        private const val ANCHOR_TOLERANCE = 1e-6f

        /** 값이 속한 눈금 칸. 칸이 바뀔 때마다 햅틱을 낸다. */
        fun tickOf(value: Float): Int = floor(value * TICKS_PER_UNIT + ANCHOR_TOLERANCE).toInt()
    }
}
