package com.stonefive.chalkak.feature.signature

import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignaturePngEncoderTest {
    private val encoder = AndroidSignaturePngEncoder()

    @Test
    fun `서명 획을 투명 배경의 PNG로 인코딩한다`() {
        val png = encoder.encode(
            strokes = listOf(
                SignatureStroke(
                    points = listOf(
                        SignaturePoint(0.2f, 0.2f),
                        SignaturePoint(0.5f, 0.7f),
                        SignaturePoint(0.8f, 0.4f),
                    ),
                ),
            ),
        )

        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)

        assertEquals(1024, bitmap.width)
        assertEquals(512, bitmap.height)
        assertEquals(0, bitmap.getPixel(0, 0).ushr(24))
        assertTrue(bitmap.hasAlpha())
    }
}
