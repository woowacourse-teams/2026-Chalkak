package com.stonefive.chalkak.feature.home.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomePhotoCardTest {
    @Test
    fun imageAspectRatioUsesImagePixelDimensions() {
        assertEquals(
            0.75f,
            imageAspectRatio(width = 1_080, height = 1_440) ?: error("missing ratio"),
            0f,
        )
    }

    @Test
    fun imageAspectRatioRejectsInvalidDimensions() {
        assertNull(imageAspectRatio(width = 0, height = 1_440))
        assertNull(imageAspectRatio(width = 1_080, height = 0))
    }
}
