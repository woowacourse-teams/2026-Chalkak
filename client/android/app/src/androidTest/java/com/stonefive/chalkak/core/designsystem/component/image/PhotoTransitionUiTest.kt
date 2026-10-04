package com.stonefive.chalkak.core.designsystem.component.image

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil3.request.ImageRequest
import com.stonefive.chalkak.R
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalSharedTransitionApi::class)
class PhotoTransitionUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun failedOriginalKeepsLoadedSourceVisible() {
        val coordinator = PhotoTransitionCoordinator()
        val key = PhotoTransitionKey("post", "record:post")
        coordinator.register(
            SharedPhotoSnapshot(
                key = key,
                imageModel = "thumbnail",
                signatureModel = null,
                painter = ColorPainter(Color.Red),
                image = null,
                memoryCacheKey = null,
                aspectRatio = 1f,
            ),
        )
        coordinator.select(key)
        val failed = AtomicBoolean(false)
        val request = ImageRequest
            .Builder(InstrumentationRegistry.getInstrumentation().targetContext)
            .data("android.resource://com.stonefive.chalkak/0")
            .listener(onError = { _, _ -> failed.set(true) })
            .build()

        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalPhotoTransitionCoordinator provides coordinator,
            ) {
                SharedPhotoImage(
                    key = key,
                    originalImageModel = request,
                    thumbnailImageModel = null,
                    signatureModel = null,
                    thumbnailSignatureModel = null,
                    contentDescription = "사진",
                    modifier = Modifier.size(100.dp).testTag("photo"),
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { failed.get() }
        composeRule.waitForIdle()
        val pixels = composeRule
            .onNodeWithTag("photo")
            .captureToImage()
            .toPixelMap()
        assertEquals(Color.Red, pixels[pixels.width / 2, pixels.height / 2])
    }

    @Test
    fun loadedSourceMatchesDestinationAndReturnsToSamePhoto() {
        val coordinator = PhotoTransitionCoordinator()
        val key = PhotoTransitionKey("post", "record:post")
        var transitionActive = false
        var showFeed by mutableStateOf(false)
        val imageModel = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}"

        composeRule.setContent {
            SharedTransitionLayout {
                val sharedScope = this
                SideEffect { transitionActive = isTransitionActive }
                AnimatedContent(targetState = showFeed, label = "test-photo") { isFeed ->
                    PhotoTransitionProvider(sharedScope, this, coordinator) {
                        if (isFeed) {
                            SharedPhotoImage(
                                key = key,
                                originalImageModel = null,
                                thumbnailImageModel = imageModel,
                                signatureModel = null,
                                thumbnailSignatureModel = null,
                                contentDescription = "사진",
                                modifier = Modifier.size(240.dp).testTag("destination"),
                            )
                        } else {
                            val source = rememberSharedPhotoSource(key, imageModel, null)
                            ChalkakImage(
                                model = imageModel,
                                contentDescription = "사진",
                                onSuccess = source.onSuccess,
                                modifier = source.modifier.size(100.dp).testTag("source").clickable {
                                    source.select()
                                    showFeed = true
                                },
                            )
                        }
                    }
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            coordinator.select(key)?.painter != null
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("source").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle { assertTrue(transitionActive) }
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.onNodeWithTag("destination").assertIsDisplayed()
        composeRule.runOnIdle {
            assertNotNull(coordinator.selectedSnapshot("post")?.painter)
            showFeed = false
        }
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.onNodeWithTag("source").assertIsDisplayed()
    }
}
