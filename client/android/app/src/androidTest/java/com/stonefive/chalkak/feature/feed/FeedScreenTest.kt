package com.stonefive.chalkak.feature.feed

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.dialog.CONFIRM_BUTTON_TEST_TAG
import com.stonefive.chalkak.core.designsystem.component.image.LocalPhotoTransitionCoordinator
import com.stonefive.chalkak.core.designsystem.component.image.PhotoTransitionCoordinator
import com.stonefive.chalkak.core.designsystem.component.image.PhotoTransitionKey
import com.stonefive.chalkak.core.designsystem.component.image.SharedPhotoSnapshot
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.AiReview
import com.stonefive.chalkak.domain.model.AiReviewContent
import com.stonefive.chalkak.domain.model.AiReviewFeedback
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.feature.aireview.AiReviewPhase
import com.stonefive.chalkak.feature.aireview.AiReviewUiState
import java.io.File
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

    @Test
    fun readyAiPillOpensStartSheetAndStartsReview() {
        var started = false
        setFeedContent(
            uiState = feedUiState.copy(aiReview = AiReviewUiState(phase = AiReviewPhase.Ready)),
            onStartFeedback = { started = true },
        )
        composeRule
            .onNodeWithContentDescription("AI 피드백 받기")
            .assertWidthIsEqualTo(148.dp)
            .assertHeightIsEqualTo(44.dp)
        val textLayout = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText("AI 피드백 받기").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(textLayout)
        }
        assertEquals(
            14.sp,
            textLayout
                .single()
                .layoutInput.style.fontSize,
        )
        screenshot("ready")
        composeRule.onNodeWithText("AI 피드백 받기").performClick()
        composeRule.onNodeWithText("AI 피드백을 시작할까요?").assertIsDisplayed()
        screenshot("start-sheet")
        composeRule.onNodeWithText("분석 시작하기").performClick()
        assertTrue(started)
    }

    @Test
    fun processingAiPillIsDisabled() {
        setFeedContent(uiState = feedUiState.copy(aiReview = AiReviewUiState(phase = AiReviewPhase.Processing)))
        composeRule
            .onNodeWithContentDescription("AI 분석중")
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .assertWidthIsEqualTo(120.dp)
            .assertHeightIsEqualTo(44.dp)
    }

    @Test
    fun completedAiPillOpensResultSheet() {
        setFeedContent(
            uiState = feedUiState.copy(
                aiReview = AiReviewUiState(phase = AiReviewPhase.Completed, result = sampleAiResult),
            ),
        )
        screenshot("completed")
        composeRule.onNodeWithText("AI 피드백 보기").performClick()
        composeRule.onNodeWithText("AI 피드백이 완료되었어요").assertIsDisplayed()
        composeRule.onNodeWithText("전체 요약").assertIsDisplayed()
        screenshot("result-sheet")
        composeRule.onNodeWithTag("ai-review-drag-handle-touch").performClick()
        composeRule.onNodeWithText("AI 피드백이 완료되었어요").assertDoesNotExist()
    }

    @Test
    fun notificationEntryOpensLatestCompletedResultOnce() {
        var state by mutableStateOf(feedUiState.copy(aiReview = AiReviewUiState()))
        composeRule.setContent {
            ChalkakTheme {
                FeedScreen(
                    uiState = state,
                    onNavigateBack = {},
                    onDeleteClick = {},
                    onLikeClick = {},
                    snackbarHostState = SnackbarHostState(),
                    openAiReviewResult = true,
                )
            }
        }
        composeRule.onNodeWithText("AI 피드백이 완료되었어요").assertDoesNotExist()
        composeRule.runOnIdle {
            state = state.copy(
                aiReview = AiReviewUiState(
                    phase = AiReviewPhase.Completed,
                    result = sampleAiResult,
                    status = AiReview.Status.COMPLETED,
                ),
            )
        }
        composeRule.onNodeWithText("AI 피드백이 완료되었어요").assertIsDisplayed()
        composeRule.onNodeWithTag("ai-review-drag-handle-touch").performClick()
        composeRule.onNodeWithText("AI 피드백이 완료되었어요").assertDoesNotExist()
        composeRule.runOnIdle { state = state.copy(isUpdatingTitle = true) }
        composeRule.onNodeWithText("AI 피드백이 완료되었어요").assertDoesNotExist()
    }

    @Test
    fun notificationEntryShowsFailureWhenLatestGetFails() {
        var state by mutableStateOf(feedUiState.copy(aiReview = AiReviewUiState()))
        composeRule.setContent {
            ChalkakTheme {
                FeedScreen(
                    uiState = state,
                    onNavigateBack = {},
                    onDeleteClick = {},
                    onLikeClick = {},
                    snackbarHostState = SnackbarHostState(),
                    openAiReviewResult = true,
                )
            }
        }
        composeRule.runOnIdle {
            state = state.copy(aiReview = AiReviewUiState(status = AiReview.Status.FAILED))
        }
        composeRule.onNodeWithText("AI 피드백을 생성하지 못했어요").assertIsDisplayed()
        composeRule.onNodeWithText("이 사진은 더 이상 AI 피드백을 요청할 수 없어요.").assertIsDisplayed()
    }

    @Test
    fun notificationEntryForPastPhotoExplainsTodayOnlyRetry() {
        var state by mutableStateOf(feedUiState.copy(aiReview = AiReviewUiState()))
        composeRule.setContent {
            ChalkakTheme {
                FeedScreen(
                    uiState = state,
                    onNavigateBack = {},
                    onDeleteClick = {},
                    onLikeClick = {},
                    snackbarHostState = SnackbarHostState(),
                    openAiReviewResult = true,
                )
            }
        }
        composeRule.runOnIdle {
            state = state.copy(aiReview = AiReviewUiState(status = AiReview.Status.FAILED, canRequestAgain = true))
        }
        composeRule.onNodeWithText("AI 피드백은 오늘 올린 사진만 재요청할 수 있어요.").assertIsDisplayed()
    }

    @Test
    fun notificationEntryWithRetryLeftOpensStartSheet() {
        var state by mutableStateOf(feedUiState.copy(aiReview = AiReviewUiState()))
        composeRule.setContent {
            ChalkakTheme {
                FeedScreen(
                    uiState = state,
                    onNavigateBack = {},
                    onDeleteClick = {},
                    onLikeClick = {},
                    snackbarHostState = SnackbarHostState(),
                    openAiReviewResult = true,
                )
            }
        }
        composeRule.runOnIdle {
            state = state.copy(aiReview = failedAiReview(canRequestAgain = true))
        }
        composeRule.onNodeWithText("AI 피드백을 재요청할까요?").assertIsDisplayed()
    }

    @Test
    fun failedPillWithRetryLeftOpensStartSheetAndStartsFeedback() {
        var started = false
        setFeedContent(
            uiState = feedUiState.copy(aiReview = failedAiReview(canRequestAgain = true)),
            onStartFeedback = { started = true },
        )
        composeRule.onNodeWithText("AI 피드백 재요청").performClick()
        composeRule.onNodeWithText("AI 피드백을 재요청할까요?").assertIsDisplayed()
        composeRule.onNodeWithText("분석 시작하기").performClick()
        composeRule.runOnIdle { assertTrue(started) }
    }

    @Test
    fun failedPillWithoutRetryExplainsAndCloses() {
        setFeedContent(uiState = feedUiState.copy(aiReview = failedAiReview(canRequestAgain = false)))
        composeRule.onNodeWithText("AI 피드백 실패").performClick()
        composeRule.onNodeWithText("이 사진은 더 이상 AI 피드백을 요청할 수 없어요.").assertIsDisplayed()
        composeRule.onNodeWithText("확인").performClick()
        composeRule.onNodeWithText("이 사진은 더 이상 AI 피드백을 요청할 수 없어요.").assertDoesNotExist()
    }

    @Test
    fun hiddenAiReviewDoesNotShowPillForOwnedPost() {
        setFeedContent(uiState = feedUiState.copy(aiReview = AiReviewUiState(phase = AiReviewPhase.Hidden)))
        composeRule.onAllNodesWithText("AI 피드백 받기").assertCountEquals(0)
    }

    @Test
    fun anotherUsersPostDoesNotShowAiPill() {
        setFeedContent(
            uiState = feedUiState.copy(
                content = feedUiState.content?.copy(
                    post = feedUiState.content.post
                        .copy(isOwnedByCurrentUser = false),
                ),
                aiReview = AiReviewUiState(phase = AiReviewPhase.Ready),
            ),
        )
        composeRule.onAllNodesWithText("AI 피드백 받기").assertCountEquals(0)
    }

    @Test
    fun aiPillTracksPhotoWhileScrolling() {
        composeRule.setContent {
            ChalkakTheme {
                Box(modifier = Modifier.height(500.dp)) {
                    FeedScreen(
                        uiState = feedUiState.copy(aiReview = AiReviewUiState(phase = AiReviewPhase.Ready)),
                        onNavigateBack = {},
                        onDeleteClick = {},
                        onLikeClick = {},
                        snackbarHostState = SnackbarHostState(),
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("AI 피드백 받기").assertIsDisplayed()
        screenshot("over-photo")
        val photo = composeRule.onNodeWithContentDescription("사진")
        val photoTopBefore = photo
            .fetchSemanticsNode()
            .boundsInRoot.top
        photo.performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        assertTrue(
            photo
                .fetchSemanticsNode()
                .boundsInRoot.top < photoTopBefore,
        )
        composeRule.onNodeWithContentDescription("AI 피드백 받기").assertIsDisplayed()
        screenshot("below-photo")
    }

    private fun screenshot(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "feed-review")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun setFeedContent(
        uiState: FeedUiState = feedUiState,
        onNavigateBack: () -> Unit = {},
        onDeleteClick: () -> Unit = {},
        onLikeClick: () -> Unit = {},
        onStartFeedback: () -> Unit = {},
    ) {
        composeRule.setContent {
            ChalkakTheme {
                FeedScreen(
                    uiState = uiState,
                    onNavigateBack = onNavigateBack,
                    onDeleteClick = onDeleteClick,
                    onLikeClick = onLikeClick,
                    onStartFeedback = onStartFeedback,
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }
    }
}

private fun failedAiReview(canRequestAgain: Boolean) = AiReviewUiState(
    phase = AiReviewPhase.Failed,
    status = AiReview.Status.FAILED,
    canRequestAgain = canRequestAgain,
)

private val sampleAiResult = AiReviewContent(
    overallScore = 8.7,
    criteria = emptyList(),
    distinctivePerspective = false,
    feedback = AiReviewFeedback("전체 요약", "장점", "아쉬운 점", "제안"),
)

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
