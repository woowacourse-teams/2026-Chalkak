package com.stonefive.chalkak.feature.upload

import org.junit.Assert.assertEquals
import org.junit.Test

class KoreanObjectParticleTest {
    @Test
    fun `받침이 없는 주제에는 를을 사용한다`() {
        assertEquals("를", "바다".koreanObjectParticle())
    }

    @Test
    fun `받침이 있는 주제에는 을을 사용한다`() {
        assertEquals("을", "틈".koreanObjectParticle())
    }
}
