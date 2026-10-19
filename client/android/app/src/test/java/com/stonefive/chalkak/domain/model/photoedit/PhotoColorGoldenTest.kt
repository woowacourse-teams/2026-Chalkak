package com.stonefive.chalkak.domain.model.photoedit

import org.junit.Assert.assertEquals
import org.junit.Test

/** 색 변환과 자동 보정 계산을 옮기거나 나눌 때 결과가 그대로인지 확인한다. */
class PhotoColorGoldenTest {
    @Test
    fun `노출과 따뜻함`() {
        assertTransform(
            PhotoAdjustments(exposure = 0.4f, warmth = -0.3f),
            "FF00FF00 FF16FF44 FF29FF85 FF3DF5C6 FF50E1FF FF64CD0E FF78B94F FF8BA490" +
                " FF9F90D1 FFB27CFF FFC66819 FFD9545A FFED3F9B FFFF2BDC FFFF17FF FFFF0024",
        )
    }

    @Test
    fun `톤 곡선`() {
        assertTransform(
            PhotoAdjustments(contrast = 0.5f, shadows = 0.4f, highlights = -0.6f, blackPoint = 0.3f),
            "FF00FF00 FF00EB1D FF0BD751 FF1CC38C FF2CAECD FF3C9900 FF4D8526 FF5F725B" +
                " FF725F97 FF854DD7 FF993C00 FFAE2C2E FFC31C64 FFD70BA2 FFEB00E2 FFFF0004",
        )
    }

    @Test
    fun `채도와 음수 노출`() {
        assertTransform(
            PhotoAdjustments(exposure = -0.2f, saturation = 0.6f),
            "FF00F900 FF00E900 FF00D700 FF00C582 FF00B2D3 FF00A400 FF3D9200 FF647E5E" +
                " FF8067AB FF974AEC FFB33B00 FFC70416 FFDA0076 FFED00B7 FFFE00F5 FFFF0000",
        )
    }

    @Test
    fun `흑백과 검은색 띄우기`() {
        assertTransform(
            PhotoAdjustments(saturation = -1f, blackPoint = -0.5f),
            "FFE1E1E1 FFD5D5D5 FFCACACA FFC0C0C0 FFB9B9B9 FFA6A6A6 FF9D9D9D FF969696" +
                " FF929292 FF939393 FF828282 FF838383 FF878787 FF8E8E8E FF999999 FF929292",
        )
    }

    @Test
    fun `모든 조정과 필터`() {
        assertTransform(
            PhotoAdjustments(0.3f, -0.4f, 0.5f, -0.2f, 0.1f, 0.4f, 0.7f),
            PhotoFilter.Vintage,
            "FF6BD047 FF6BD047 FF6BD047 FF76D692 FF77CEBA FF7BB542 FF9AAD44 FFB1A97B" +
                " FFC4A1A2 FFD295BF FFC6753B FFBF5037 FFBE416B FFC54791 FFCA4BAB FFB33729",
        )
    }

    @Test
    fun `필터만`() {
        assertTransform(
            PhotoAdjustments(),
            PhotoFilter.Mono,
            "FF969696 FF979797 FF989898 FF999999 FF9A9A9A FF7E7E7E FF7F7F7F FF818181" +
                " FF828282 FF838383 FF676767 FF686868 FF696969 FF6A6A6A FF6B6B6B FF4F4F4F",
        )
    }

    @Test
    fun `자동 보정값`() {
        assertEquals(
            PhotoAdjustments(exposure = 0.67f, contrast = 0.4f, shadows = 0.35f, saturation = 0.18f),
            PhotoAutoAdjuster.adjustments(statistics(0 until 80, saturation90 = 0.4)),
        )
        assertEquals(
            PhotoAdjustments(
                exposure = -0.19f,
                contrast = 0.4f,
                highlights = -0.26f,
                blackPoint = 0.83f,
                saturation = 0.25f,
            ),
            PhotoAutoAdjuster.adjustments(statistics(180 until 256, saturation90 = 0.2)),
        )
        assertEquals(
            PhotoAdjustments(highlights = -0.31f, shadows = 0.2f, saturation = 0.06f),
            PhotoAutoAdjuster.adjustments(statistics(0 until 256, saturation90 = 0.6)),
        )
        assertEquals(
            PhotoAdjustments(exposure = 0.67f, contrast = 0.4f, blackPoint = 0.83f, saturation = 0.24f),
            PhotoAutoAdjuster.adjustments(statistics(100 until 140, saturation90 = 0.3)),
        )
    }

    @Test
    fun `픽셀 통계와 그 자동 보정값`() {
        val statistics = PhotoImageStatistics.of(PhotoPixels(16, 16, IntArray(256) { pattern(it) }))

        assertEquals(1.0, statistics.saturation90, 0.0)
        assertEquals(127 / 255.0, statistics.luminancePercentile(0.5), 0.0)
        assertEquals(201 / 255.0, statistics.luminancePercentile(0.9), 0.0)
        assertEquals(
            PhotoAdjustments(
                exposure = 0.05f,
                contrast = 0.07f,
                highlights = -0.15f,
                shadows = 0.16f,
                blackPoint = 0.12f,
            ),
            PhotoAutoAdjuster.adjustments(statistics),
        )
    }

    private fun assertTransform(
        adjustments: PhotoAdjustments,
        expected: String,
    ) = assertTransform(adjustments, PhotoFilter.Original, expected)

    private fun assertTransform(
        adjustments: PhotoAdjustments,
        filter: PhotoFilter,
        expected: String,
    ) {
        val pixels = IntArray(16) { i ->
            (0xFF shl 24) or ((i * 17) shl 16) or ((255 - i * 17) shl 8) or ((i * 53) % 256)
        }

        PhotoColorTransform(adjustments, filter.colorMatrix()).apply(pixels)

        assertEquals(expected, pixels.joinToString(" ") { "%08X".format(it) })
    }

    private fun statistics(
        levels: IntRange,
        saturation90: Double,
    ) = PhotoImageStatistics(
        luminanceHistogram = DoubleArray(256) { if (it in levels) 1.0 / levels.count() else 0.0 },
        saturation90 = saturation90,
    )

    private fun pattern(index: Int) =
        (0xFF shl 24) or ((index * 7 % 256) shl 16) or ((index * 13 % 256) shl 8) or (index * 29 % 256)
}
