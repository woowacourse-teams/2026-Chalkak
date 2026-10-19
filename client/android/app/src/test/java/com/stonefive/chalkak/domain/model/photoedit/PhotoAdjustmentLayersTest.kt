package com.stonefive.chalkak.domain.model.photoedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoAdjustmentLayersTest {
    private val automatic = PhotoAdjustments(exposure = 0.4f, contrast = 0.2f)

    @Test
    fun `직접 만진 항목은 그 값을, 나머지는 자동 보정값 곱하기 강도를 적용한다`() {
        val layers = PhotoAdjustmentLayers(
            automatic = automatic,
            intensity = 0.5f,
            manual = mapOf(PhotoAdjustment.Exposure to 0.9f, PhotoAdjustment.Warmth to 0.1f),
        )

        assertEquals(PhotoAdjustments(exposure = 0.9f, contrast = 0.1f, warmth = 0.1f), layers.applied)
    }

    @Test
    fun `자동 보정보다 낮춘 값은 자동 보정을 끄거나 강도를 바꿔도 원본 아래로 내려가지 않고 그대로다`() {
        val lowered = PhotoAdjustmentLayers(automatic = automatic, intensity = 1f)
            .withValue(PhotoAdjustment.Exposure, 0.3f)

        val halved = lowered.withIntensity(0.5f)
        assertEquals(0.3f, halved.applied.exposure)
        assertEquals(0.1f, halved.applied.contrast, TOLERANCE)

        val off = lowered.withIntensity(0f)
        assertEquals(PhotoAdjustments(exposure = 0.3f), off.applied)
    }

    @Test
    fun `자동 보정 몫으로 되돌리면 다시 자동 보정을 따른다`() {
        val layers = PhotoAdjustmentLayers(automatic = automatic, intensity = 1f)
            .withValue(PhotoAdjustment.Exposure, 0.3f)
            .withValue(PhotoAdjustment.Exposure, 0.4f)

        assertTrue(layers.manual.isEmpty())
        assertEquals(
            0.2f,
            layers
                .withIntensity(0.5f)
                .applied.exposure,
            TOLERANCE,
        )
    }

    @Test
    fun `자동 보정이 꺼져 있으면 0으로 되돌린 항목은 직접 만진 것으로 남지 않는다`() {
        val layers = PhotoAdjustmentLayers(automatic = automatic)
            .withValue(PhotoAdjustment.Exposure, 0.3f)
            .withValue(PhotoAdjustment.Exposure, -0f)

        assertTrue(layers.manual.isEmpty())
        assertFalse(layers.isAdjusted)
    }

    @Test
    fun `자동 보정이 꺼져 있거나 분석 전이면 자동 보정 몫이 없다`() {
        assertNull(PhotoAdjustmentLayers(automatic = automatic).appliedAutomatic)
        assertNull(PhotoAdjustmentLayers(intensity = 1f).appliedAutomatic)
        assertEquals(PhotoAdjustments(), PhotoAdjustmentLayers(intensity = 1f).applied)
    }

    @Test
    fun `새 자동 보정값을 받아도 직접 만진 값은 유지하고 초기화해도 자동 보정값은 남긴다`() {
        val manual = mapOf(PhotoAdjustment.Warmth to 0.3f)
        val layers = PhotoAdjustmentLayers(automatic = automatic, intensity = 1f, manual = manual)

        val refreshed = layers.withAutomatic(PhotoAdjustments(contrast = 0.5f))
        assertEquals(manual, refreshed.manual)
        assertEquals(PhotoAdjustments(contrast = 0.5f, warmth = 0.3f), refreshed.applied)

        val cleared = layers.cleared()
        assertEquals(automatic, cleared.automatic)
        assertTrue(cleared.applied.isIdentity)
        assertFalse(cleared.isAdjusted)
    }

    @Test
    fun `적용 값만 있으면 모두 직접 정한 값으로 둔다`() {
        val layers = PhotoAdjustmentLayers.manualOnly(PhotoAdjustments(exposure = 0.2f, saturation = -1f))

        assertEquals(mapOf(PhotoAdjustment.Exposure to 0.2f, PhotoAdjustment.Saturation to -1f), layers.manual)
        assertEquals(PhotoAdjustments(exposure = 0.2f, saturation = -1f), layers.applied)
    }

    private companion object {
        const val TOLERANCE = 1e-6f
    }
}
