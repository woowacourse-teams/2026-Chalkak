package com.stonefive.chalkak.feature.feed

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.dialog.CONFIRM_BUTTON_TEST_TAG
import com.stonefive.chalkak.core.designsystem.component.image.LocalPhotoTransitionCoordinator
import com.stonefive.chalkak.core.designsystem.component.image.PhotoTransitionCoordinator
import com.stonefive.chalkak.core.designsystem.component.image.PhotoTransitionKey
import com.stonefive.chalkak.core.designsystem.component.image.SharedPhotoSnapshot
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FeedScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recordPhotoNodeSurvivesDetailLoading() {
        val post = checkNotNull(feedUiState.content).post
        val coordinator = PhotoTransitionCoordinator()
        val key = PhotoTransitionKey(post.id, "record:${post.id}")
        coordinator.register(
            SharedPhotoSnapshot(
                key = key,
                imageModel = post.thumbnailImageUrl,
                signatureModel = null,
                painter = ColorPainter(Color.Red),
                image = null,
                memoryCacheKey = null,
                aspectRatio = 1f,
            ),
        )
        coordinator.select(key)
        var state by mutableStateOf(FeedUiState(isLoading = true))
        composeRule.setContent {
            ChalkakTheme {
                CompositionLocalProvider(LocalPhotoTransitionCoordinator provides coordinator) {
                    FeedScreen(
                        uiState = state,
                        onNavigateBack = {},
                        onDeleteClick = {},
                        onLikeClick = {},
                        snackbarHostState = SnackbarHostState(),
                        entryPostId = post.id,
                        entryThumbnailImageUrl = post.thumbnailImageUrl,
                    )
                }
            }
        }
        val initialNodeId = composeRule
            .onNodeWithContentDescription("기록 사진")
            .fetchSemanticsNode()
            .id
        composeRule.runOnIdle { state = feedUiState }
        val loadedNodeId = composeRule
            .onNodeWithContentDescription(post.contentDescription)
            .fetchSemanticsNode()
            .id
        assertEquals("상세 로딩 후에도 사진 노드를 유지해야 합니다", initialNodeId, loadedNodeId)
    }

    @Test
    fun recordEntryShowsPhotoWhilePostDetailIsLoading() {
        composeRule.setContent {
            ChalkakTheme {
                FeedScreen(
                    uiState = FeedUiState(isLoading = true),
                    onNavigateBack = {},
                    onDeleteClick = {},
                    onLikeClick = {},
                    snackbarHostState = SnackbarHostState(),
                    entryPostId = "record-post",
                    entryThumbnailImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}",
                )
            }
        }

        composeRule.onNodeWithContentDescription("기록 사진").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("삭제").assertCountEquals(0)
    }

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
    fun confirmingDeleteInvokesCallback() {
        var deleteClicked = false
        setFeedContent(onDeleteClick = { deleteClicked = true })

        composeRule
            .onNodeWithContentDescription("삭제")
            .assertHasClickAction()
            .performClick()

        assertFalse(deleteClicked)
        composeRule.onNodeWithText("게시물을 삭제하시겠어요?").assertIsDisplayed()
        composeRule.onNodeWithTag(CONFIRM_BUTTON_TEST_TAG).performClick()

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
    fun editButtonIsHiddenForPastTopic() {
        setFeedContent()

        composeRule.onAllNodesWithContentDescription("수정").assertCountEquals(0)
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
                    snackbarHostState = SnackbarHostState(),
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
            originalImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}",
            thumbnailImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}",
            signatureOriginalImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
            signatureThumbnailImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
            contentDescription = "사진",
            title = "안녕하세요 찰캌입니다.",
            likeCount = 24,
            isOwnedByCurrentUser = true,
        ),
        isLiked = false,
        topicDate = LocalDate.of(2026, 8, 3),
    ),
)
