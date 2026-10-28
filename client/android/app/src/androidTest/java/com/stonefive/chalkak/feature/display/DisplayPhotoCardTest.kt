package com.stonefive.chalkak.feature.display

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakSignedImage
import com.stonefive.chalkak.core.designsystem.component.image.LocalPhotoTransitionCoordinator
import com.stonefive.chalkak.core.designsystem.component.image.PhotoTransitionCoordinator
import com.stonefive.chalkak.core.designsystem.component.image.PhotoTransitionKey
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.display.component.DisplayPhotoCard
import com.stonefive.chalkak.feature.display.component.DisplayPhotoCardVariant
import com.stonefive.chalkak.feature.display.component.previewDisplayPost
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class DisplayPhotoCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun featuredPhotoUsesLoadedImageBoundsInsideFixedCard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, R.drawable.home_feed_photo, options)
        val expectedRatio = options.outWidth.toFloat() / options.outHeight
        val coordinator = PhotoTransitionCoordinator()

        val photo = previewDisplayPost.copy(
            originalImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}",
            thumbnailImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}",
        )

        composeRule.setContent {
            ChalkakTheme {
                CompositionLocalProvider(LocalPhotoTransitionCoordinator provides coordinator) {
                    DisplayPhotoCard(
                        photo = photo,
                        variant = DisplayPhotoCardVariant.FEATURED,
                        modifier = Modifier.size(width = 180.dp, height = 240.dp).testTag("card"),
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val photo = photoBounds(previewDisplayPost.contentDescription)
            abs(photo.width / photo.height - expectedRatio) < 0.02f
        }
        assertPhotoCentered(previewDisplayPost.contentDescription, expectedRatio)
        composeRule.runOnIdle {
            val snapshot = coordinator.select(PhotoTransitionKey(photo.id, "display-FEATURED:${photo.id}"))
            assertNotNull(snapshot)
            assertNull(snapshot?.sourceShape)
        }
    }

    @Test
    fun tallPhotoFitsCardHeightWithoutChangingItsAspectRatio() {
        composeRule.setContent {
            ChalkakTheme {
                ChalkakSignedImage(
                    imageModel = null,
                    signatureModel = null,
                    contentDescription = "tall photo",
                    loadedImagePainter = ColorPainter(Color.Red),
                    imageAspectRatio = 0.5f,
                    modifier = Modifier.size(width = 180.dp, height = 240.dp).testTag("card"),
                )
            }
        }

        assertPhotoCentered("tall photo", 0.5f)
        val card = composeRule
            .onNodeWithTag("card")
            .fetchSemanticsNode()
            .boundsInRoot
        assertEquals(card.height, photoBounds("tall photo").height, 1f)
    }

    @Test
    fun tappingCardBottomStartCornerLikesInsteadOfOpeningPhoto() {
        var photoClickCount = 0
        var likeClickCount = 0
        composeRule.setContent {
            ChalkakTheme {
                DisplayPhotoCard(
                    photo = previewDisplayPost,
                    modifier = Modifier.size(180.dp).testTag("card"),
                    onClick = { photoClickCount++ },
                    onLikeClick = { likeClickCount++ },
                )
            }
        }

        // 하트는 카드 모서리에서 10dp 안쪽에 그려진다. 하트와 모서리 사이(모서리에서 6dp)를 눌러도 좋아요여야 한다.
        // 6dp는 둥근 모서리(반지름 12dp) 안쪽이라 카드 위를 누른 것으로 판정된다.
        composeRule.onNodeWithTag("card").performTouchInput {
            val inset = 6.dp.toPx()
            click(Offset(inset, height - inset))
        }

        composeRule.runOnIdle {
            assertEquals(1, likeClickCount)
            assertEquals(0, photoClickCount)
        }
    }

    @Test
    fun authorPhotosHideUnavailableLikeInformation() {
        composeRule.setContent {
            ChalkakTheme {
                DisplayPhotoCard(
                    photo = previewDisplayPost,
                    showLikeCount = false,
                    modifier = Modifier.size(180.dp),
                )
            }
        }

        composeRule.onNodeWithContentDescription("좋아요 ${previewDisplayPost.likeCount}").assertDoesNotExist()
    }

    private fun assertPhotoCentered(
        description: String,
        expectedRatio: Float,
    ) {
        val card = composeRule
            .onNodeWithTag("card")
            .fetchSemanticsNode()
            .boundsInRoot
        val photo = photoBounds(description)
        assertEquals(0.75f, card.width / card.height, 0.02f)
        assertEquals(expectedRatio, photo.width / photo.height, 0.02f)
        assertEquals(card.center.x, photo.center.x, 1f)
        assertEquals(card.center.y, photo.center.y, 1f)
    }

    private fun photoBounds(description: String): Rect = composeRule
        .onNodeWithContentDescription(description, useUnmergedTree = true)
        .fetchSemanticsNode()
        .boundsInRoot
}
