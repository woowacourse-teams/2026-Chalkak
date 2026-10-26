package com.stonefive.chalkak.feature.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.feature.feed.component.FeedPhoto
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FeedSignatureHintTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hintIsAnAnchoredPopupAndDismissesOnTap() {
        var showHint by mutableStateOf(true)
        composeRule.setContent {
            ChalkakTheme {
                FeedPhoto(
                    post = signaturePost,
                    isLiked = false,
                    onLikeClick = {},
                    showSignatureHint = showHint,
                    onDismissSignatureHint = { showHint = false },
                )
            }
        }

        composeRule.onNode(isPopup()).assertIsDisplayed()
        composeRule
            .onNodeWithText(SIGNATURE_HINT)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText(SIGNATURE_HINT).assertDoesNotExist()
    }

    @Test
    fun nonFocusableHintDoesNotBlockTheSignatureClick() {
        var signatureClicks = 0
        composeRule.setContent {
            ChalkakTheme {
                FeedPhoto(
                    post = signaturePost,
                    isLiked = false,
                    onLikeClick = {},
                    showSignatureHint = true,
                    onSignatureClick = { signatureClicks++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("작성자 사인").performClick()
        composeRule.runOnIdle { assertEquals(1, signatureClicks) }
    }
}

private const val SIGNATURE_HINT = "사인을 누르면 이 작가님의 사진을 모아볼 수 있어요"

private val signaturePost = Post(
    id = "hint-post",
    authorId = "author",
    originalImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}",
    thumbnailImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}",
    signatureOriginalImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
    contentDescription = "사진",
    title = null,
    likeCount = 0,
)
