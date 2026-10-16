package com.stonefive.chalkak.core.designsystem.component.image

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoTransitionTest {
    @Test
    fun `shared element key includes post and source`() {
        val key = PhotoTransitionKey(
            postId = "post-1",
            sourceId = "display-grid:post-1",
        )

        assertEquals("post-photo:post-1:display-grid:post-1", key.sharedElementKey)
    }

    @Test
    fun `coordinator resolves selected snapshot per post`() {
        val coordinator = PhotoTransitionCoordinator()
        val gridKey = PhotoTransitionKey("post-1", "display-grid:post-1")
        val recordKey = PhotoTransitionKey("post-1", "record:post-1")
        val gridSnapshot = SharedPhotoSnapshot(
            key = gridKey,
            imageModel = "grid",
            signatureModel = null,
            painter = null,
            image = null,
            memoryCacheKey = null,
            aspectRatio = 0.75f,
        )
        val recordSnapshot = gridSnapshot.copy(
            key = recordKey,
            imageModel = "record",
        )

        coordinator.register(gridSnapshot)
        coordinator.register(recordSnapshot)

        assertEquals(gridSnapshot, coordinator.select(gridKey))
        assertEquals(gridSnapshot, coordinator.selectedSnapshot("post-1"))

        assertEquals(recordSnapshot, coordinator.select(recordKey))
        assertEquals(recordSnapshot, coordinator.selectedSnapshot("post-1"))

        coordinator.clear("post-1")
        assertNull(coordinator.selectedSnapshot("post-1"))
    }

    @Test
    fun `selected snapshot is retained after source unregisters`() {
        val coordinator = PhotoTransitionCoordinator()
        val key = PhotoTransitionKey("post-1", "record:post-1")
        val snapshot = SharedPhotoSnapshot(
            key = key,
            imageModel = "thumbnail",
            signatureModel = null,
            painter = null,
            image = null,
            memoryCacheKey = null,
            aspectRatio = 1.2f,
        )

        coordinator.register(snapshot)
        coordinator.select(key)
        coordinator.unregister(key)

        assertEquals(snapshot, coordinator.selectedSnapshot("post-1"))
    }

    @Test
    fun `layout updates cannot replace visible thumbnail with pending original`() {
        val coordinator = PhotoTransitionCoordinator()
        val key = PhotoTransitionKey("post", "featured:post")
        val loaded = SharedPhotoSnapshot(
            key = key,
            imageModel = "thumbnail",
            signatureModel = null,
            painter = ColorPainter(Color.Red),
            image = null,
            memoryCacheKey = null,
            aspectRatio = 0.75f,
        )
        coordinator.register(loaded)
        coordinator.register(loaded.copy(imageModel = "original", painter = null))

        assertEquals(loaded, coordinator.select(key))
    }

    @Test
    fun `original is revealed only after load success and enter transition finish`() {
        assertFalse(shouldRevealOriginal(isOriginalLoaded = false, isEnterFinished = false))
        assertFalse(shouldRevealOriginal(isOriginalLoaded = true, isEnterFinished = false))
        assertFalse(shouldRevealOriginal(isOriginalLoaded = false, isEnterFinished = true))
        assertTrue(shouldRevealOriginal(isOriginalLoaded = true, isEnterFinished = true))
    }
}
