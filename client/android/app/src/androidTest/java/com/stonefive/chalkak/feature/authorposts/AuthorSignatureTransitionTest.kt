package com.stonefive.chalkak.feature.authorposts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.PhotoTransitionProvider
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.feature.feed.component.FeedPhoto
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalSharedTransitionApi::class)
class AuthorSignatureTransitionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun feedSignatureMatchesAuthorHeaderAndReturns() {
        assertSignatureMatchesBothDirections(useSharedPhoto = false)
    }

    @Test
    fun sharedPhotoSignatureMatchesAuthorHeaderAndReturns() {
        assertSignatureMatchesBothDirections(useSharedPhoto = true)
    }

    private fun assertSignatureMatchesBothDirections(useSharedPhoto: Boolean) {
        var showAuthor by mutableStateOf(false)
        var transitionActive = false
        val image = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}"
        val signature = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}"
        val post = Post(
            id = "post",
            authorId = "author",
            originalImageUrl = image,
            thumbnailImageUrl = image,
            signatureOriginalImageUrl = signature,
            signatureThumbnailImageUrl = signature,
            contentDescription = "사진",
            title = null,
            likeCount = 0,
        )
        composeRule.setContent {
            ChalkakTheme {
                SharedTransitionLayout {
                    val sharedScope = this
                    SideEffect { transitionActive = isTransitionActive }
                    AnimatedContent(targetState = showAuthor, label = "author-signature") { isAuthor ->
                        PhotoTransitionProvider(sharedScope, this) {
                            if (isAuthor) {
                                AuthorPostsScreen(
                                    uiState = AuthorPostsUiState(isLoading = false),
                                    signatureThumbnailImageUrl = signature,
                                    isMine = false,
                                    onNavigateBack = { showAuthor = false },
                                    onPhotoClick = {},
                                    onRetry = {},
                                    onEndThresholdChanged = {},
                                    authorId = post.authorId,
                                )
                            } else {
                                FeedPhoto(
                                    post = post,
                                    isLiked = false,
                                    onLikeClick = {},
                                    entryThumbnailImageUrl = if (useSharedPhoto) image else null,
                                    onSignatureClick = { showAuthor = true },
                                )
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithContentDescription("작성자 사인").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle { assertTrue("사인 진입 전환이 매칭되어야 합니다", transitionActive) }
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.onNodeWithTag("author-posts-signature").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("뒤로 가기").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle { assertTrue("사인 복귀 전환이 매칭되어야 합니다", transitionActive) }
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.onNodeWithContentDescription("작성자 사인").assertIsDisplayed()
    }
}
