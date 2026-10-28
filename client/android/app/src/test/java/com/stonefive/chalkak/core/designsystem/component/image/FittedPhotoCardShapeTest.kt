package com.stonefive.chalkak.core.designsystem.component.image

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class FittedPhotoCardShapeTest {
    private val shape = FittedPhotoCardShape(RoundedCornerShape(24.dp), 0.75f)

    @Test
    fun matchingPhotoKeepsCardCorners() {
        val outline = shape.createOutline(Size(300f, 400f), LayoutDirection.Ltr, Density(1f)) as Outline.Rounded
        assertEquals(0f, outline.roundRect.left, 0.001f)
        assertEquals(0f, outline.roundRect.top, 0.001f)
        assertEquals(24f, outline.roundRect.topLeftCornerRadius.x, 0.001f)
    }

    @Test
    fun landscapePhotoUsesCardMaskOutsideItsOwnBounds() {
        val outline = shape.createOutline(Size(300f, 200f), LayoutDirection.Ltr, Density(1f)) as Outline.Rounded
        assertEquals(-100f, outline.roundRect.top, 0.001f)
        assertEquals(300f, outline.roundRect.bottom, 0.001f)
    }

    @Test
    fun tallPhotoUsesHorizontalCardInsets() {
        val outline = shape.createOutline(Size(200f, 400f), LayoutDirection.Ltr, Density(1f)) as Outline.Rounded
        assertEquals(-50f, outline.roundRect.left, 0.001f)
        assertEquals(250f, outline.roundRect.right, 0.001f)
    }

    @Test
    fun destinationHasSquareCornersAndPhotoBounds() {
        val animated = AnimatedPhotoShape(shape, mutableStateOf(0f))
        val outline = animated.createOutline(Size(300f, 200f), LayoutDirection.Ltr, Density(1f)) as Outline.Rounded
        assertEquals(0f, outline.roundRect.top, 0.001f)
        assertEquals(200f, outline.roundRect.bottom, 0.001f)
        assertEquals(0f, outline.roundRect.topLeftCornerRadius.x, 0.001f)
    }

    @Test
    fun transitionInterpolatesInsetsAndCornersTogether() {
        val animated = AnimatedPhotoShape(shape, mutableStateOf(0.5f))
        val outline = animated.createOutline(Size(300f, 200f), LayoutDirection.Ltr, Density(1f)) as Outline.Rounded
        assertEquals(-50f, outline.roundRect.top, 0.001f)
        assertEquals(250f, outline.roundRect.bottom, 0.001f)
        assertEquals(12f, outline.roundRect.topLeftCornerRadius.x, 0.001f)
    }
}
