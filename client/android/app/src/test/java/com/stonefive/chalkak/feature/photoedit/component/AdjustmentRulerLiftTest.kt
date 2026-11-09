package com.stonefive.chalkak.feature.photoedit.component

import org.junit.Assert.assertEquals
import org.junit.Test

/** 눈금이 표시선을 지난 뒤 얼마나 솟아 있는지(1이면 끝까지, 0이면 원래 높이)를 본다. */
class AdjustmentRulerLiftTest {
    @Test
    fun `표시선을 막 지난 눈금은 끝까지 솟는다`() {
        // Given 눈금이 1,000ms에 표시선을 지났다
        val passedAt = millis(1_000)
        // When  1,000ms 시점의 솟는 정도를 구한다
        val lift = liftAmount(passedAt, millis(1_000))
        // Then  솟는 정도가 1
        assertEquals(1f, lift, TOLERANCE)
    }

    @Test
    fun `지난 뒤 150ms가 흐르면 절반만 솟는다`() {
        // Given 눈금이 1,000ms에 표시선을 지났다
        val passedAt = millis(1_000)
        // When  1,150ms 시점의 솟는 정도를 구한다
        val lift = liftAmount(passedAt, millis(1_150))
        // Then  솟는 정도가 0.5
        assertEquals(0.5f, lift, TOLERANCE)
    }

    @Test
    fun `지난 뒤 300ms가 흐르면 다 가라앉는다`() {
        // Given 눈금이 1,000ms에 표시선을 지났다
        val passedAt = millis(1_000)
        // When  1,300ms와 2,000ms 시점의 솟는 정도를 구한다
        val liftAtEnd = liftAmount(passedAt, millis(1_300))
        val liftLater = liftAmount(passedAt, millis(2_000))
        // Then  둘 다 0
        assertEquals(0f, liftAtEnd, TOLERANCE)
        assertEquals(0f, liftLater, TOLERANCE)
    }

    @Test
    fun `지금 시각이 지난 시각보다 이르면 1을 넘지 않는다`() {
        // Given 눈금이 10,000ms에 표시선을 지났다
        val passedAt = millis(10_000)
        // When  오래 남아 있던 0ms 프레임 시각으로 솟는 정도를 구한다
        val lift = liftAmount(passedAt, millis(0))
        // Then  솟는 정도가 1
        assertEquals(1f, lift, TOLERANCE)
    }

    private fun millis(value: Long): Long = value * NANOS_PER_MILLI

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val TOLERANCE = 1e-4f
    }
}
