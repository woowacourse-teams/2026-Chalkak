package com.stonefive.chalkak.domain.model.photoedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoAdjustmentLayersTest {
    private val automatic = PhotoAdjustments(exposure = 0.4f, shadows = -0.2f)

    @Test
    fun `적용 값은 자동 보정값에 강도를 곱하고 사용자 값을 더한 뒤 자른다`() {
        val layers = PhotoAdjustmentLayers(
            automatic = automatic,
            intensity = 0.5f,
            manual = PhotoAdjustments(exposure = 0.9f, warmth = 0.1f),
        )

        assertEquals(PhotoAdjustments(exposure = 1f, shadows = -0.1f, warmth = 0.1f), layers.applied)
    }

    @Test
    fun `항목 값을 정하면 자동 보정 몫은 두고 사용자 값만 바꾼다`() {
        val layers = PhotoAdjustmentLayers(automatic = automatic, intensity = 0.5f)
            .withValue(PhotoAdjustment.Exposure, 0.5f)

        assertEquals(0.3f, layers.manual.exposure, TOLERANCE)
        assertEquals(0.5f, layers.applied.exposure, TOLERANCE)
        assertEquals(0.2f, layers.appliedAutomatic!!.exposure, TOLERANCE)
    }

    @Test
    fun `자동 보정이 꺼져 있거나 분석 전이면 자동 보정 몫이 없다`() {
        assertNull(PhotoAdjustmentLayers(automatic = automatic).appliedAutomatic)
        assertNull(PhotoAdjustmentLayers(intensity = 1f).appliedAutomatic)
        assertEquals(PhotoAdjustments(), PhotoAdjustmentLayers(intensity = 1f).applied)
    }

    @Test
    fun `자동 보정을 끄면 잘렸던 사용자 값이 그대로 돌아온다`() {
        val layers = PhotoAdjustmentLayers(automatic = automatic)
            .withValue(PhotoAdjustment.Exposure, 1f)
            .withIntensity(1f)
        assertEquals(1f, layers.applied.exposure)

        assertEquals(
            1f,
            layers
                .withIntensity(0f)
                .applied.exposure,
        )
    }

    @Test
    fun `새 자동 보정값을 받아도 사용자 값은 유지하고 초기화해도 자동 보정값은 남긴다`() {
        val manual = PhotoAdjustments(warmth = 0.3f)
        val layers = PhotoAdjustmentLayers(automatic = automatic, intensity = 1f, manual = manual)

        val refreshed = layers.withAutomatic(PhotoAdjustments(contrast = 0.2f))
        assertEquals(manual, refreshed.manual)
        assertEquals(PhotoAdjustments(contrast = 0.2f, warmth = 0.3f), refreshed.applied)

        val cleared = layers.cleared()
        assertEquals(automatic, cleared.automatic)
        assertTrue(cleared.applied.isIdentity)
        assertFalse(cleared.isAdjusted)
    }

    private companion object {
        const val TOLERANCE = 1e-6f
    }
}
