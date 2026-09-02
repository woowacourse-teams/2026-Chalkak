package com.stonefive.chalkak.feature.signature

import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignaturePngEncoderTest {
    private val encoder = AndroidSignaturePngEncoder()

    @Test
    fun signatureStrokesAreEncodedAsPngWithTransparentBackground() {
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
        assertTrue(
            (0 until bitmap.height).any { y ->
                (0 until bitmap.width).any { x ->
                    val pixel = bitmap.getPixel(x, y)
                    pixel.ushr(24) > 0 && pixel and 0x00FFFFFF == 0x00FFFFFF
                }
            },
        )
    }
}
