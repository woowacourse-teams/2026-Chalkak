package com.stonefive.chalkak.feature.feed

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FeedScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun feedScreenShowsTopicAndPostDetails() {
        setFeedContent()

        composeRule.onNodeWithText("8월 3일의 주제").assertIsDisplayed()
        composeRule.onNodeWithText("하늘하늘하늘").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("사진").assertIsDisplayed()
        composeRule.onNodeWithText("안녕하세요 찰캌입니다.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("좋아요 24").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("삭제").assertIsDisplayed()
    }

    @Test
    fun tappingBackButtonInvokesCallback() {
        var backClicked = false
        setFeedContent(onNavigateBack = { backClicked = true })

        composeRule
            .onNodeWithContentDescription("뒤로 가기")
            .assertHasClickAction()
            .performClick()

        assertTrue(backClicked)
    }

    @Test
    fun tappingDeleteButtonInvokesCallback() {
        var deleteClicked = false
        setFeedContent(onDeleteClick = { deleteClicked = true })

        composeRule
            .onNodeWithContentDescription("삭제")
            .assertHasClickAction()
            .performClick()

        assertTrue(deleteClicked)
    }

    @Test
    fun deleteButtonIsHiddenForAnotherUsersPost() {
        setFeedContent(
            uiState = feedUiState.copy(
                content = feedUiState.content?.copy(
                    post = feedUiState.content.post
                        .copy(isOwnedByCurrentUser = false),
                ),
            ),
        )

        composeRule.onAllNodesWithContentDescription("삭제").assertCountEquals(0)
    }

    @Test
    fun tappingLikeAreaInvokesCallback() {
        var likeClicked = false
        setFeedContent(onLikeClick = { likeClicked = true })

        composeRule
            .onNodeWithContentDescription("좋아요 24")
            .assertHasClickAction()
            .performClick()

        assertTrue(likeClicked)
    }

    private fun setFeedContent(
        uiState: FeedUiState = feedUiState,
        onNavigateBack: () -> Unit = {},
        onDeleteClick: () -> Unit = {},
        onLikeClick: () -> Unit = {},
    ) {
        composeRule.setContent {
            ChalkakTheme {
                FeedScreen(
                    uiState = uiState,
                    onNavigateBack = onNavigateBack,
                    onDeleteClick = onDeleteClick,
                    onLikeClick = onLikeClick,
                )
            }
        }
    }
}

private val feedUiState = FeedUiState(
    content = FeedContentState.Success(
        dateLabel = "8월 3일의 주제",
        topic = "하늘하늘하늘",
        post = Post(
            id = "photo-1",
            imageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}",
            signatureUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
            contentDescription = "사진",
            title = "안녕하세요 찰캌입니다.",
            likeCount = 24,
            isOwnedByCurrentUser = true,
        ),
        isLiked = false,
    ),
)
