package com.stonefive.chalkak.feature.feed.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedPhotoZoomTest {
    @Test
    fun zoomScaleIsClampedBetweenDefaultAndFourTimes() {
        assertEquals(4f, feedPhotoZoomScale(currentScale = 3f, zoomChange = 2f))
        assertEquals(1f, feedPhotoZoomScale(currentScale = 2f, zoomChange = 0.1f))
        assertEquals(3f, feedPhotoZoomScale(currentScale = 2f, zoomChange = 1.5f))
    }

    @Test
    fun pinchKeepsTouchedPhotoPointUnderMovingFingers() {
        assertEquals(
            Offset(x = 100f, y = 50f),
            feedPhotoPinchOffset(
                offset = Offset.Zero,
                previousScale = 1f,
                nextScale = 2f,
                previousCentroid = Offset(x = 100f, y = 100f),
                currentCentroid = Offset(x = 100f, y = 100f),
                size = IntSize(width = 400, height = 300),
            ),
        )
        assertEquals(
            Offset(x = 120f, y = 50f),
            feedPhotoPinchOffset(
                offset = Offset.Zero,
                previousScale = 1f,
                nextScale = 2f,
                previousCentroid = Offset(x = 100f, y = 100f),
                currentCentroid = Offset(x = 120f, y = 100f),
                size = IntSize(width = 400, height = 300),
            ),
        )
    }

    @Test
    fun zoomOffsetIsLimitedToScaledPhotoBounds() {
        val offset = feedPhotoZoomOffset(
            offset = Offset(x = 1_000f, y = -1_000f),
            scale = 3f,
            size = IntSize(width = 400, height = 300),
        )

        assertEquals(600f, offset.x)
        assertEquals(-450f, offset.y)
    }

    @Test
    fun twoFingerPanMovesPhotoAtDefaultScaleWithinBounds() {
        assertEquals(
            Offset(x = 100f, y = 100f),
            feedPhotoZoomOffset(
                offset = Offset(x = 100f, y = 100f),
                scale = 1f,
                size = IntSize(width = 400, height = 300),
            ),
        )
        assertEquals(
            Offset(x = 200f, y = -150f),
            feedPhotoZoomOffset(
                offset = Offset(x = 1_000f, y = -1_000f),
                scale = 1f,
                size = IntSize(width = 400, height = 300),
            ),
        )
    }
}
