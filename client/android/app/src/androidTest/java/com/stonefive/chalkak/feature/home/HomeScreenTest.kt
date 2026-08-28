package com.stonefive.chalkak.feature.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeLike
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostPage
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.HomeRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersAllInitialAndContentStates() {
        var uiState by mutableStateOf(HomeUiState(contentStatus = HomeContentStatus.Loading))
        composeRule.setContent {
            ChalkakTheme {
                HomeScreen(
                    uiState = uiState,
                    snackbarHostState = remember { SnackbarHostState() },
                    onAction = {},
                )
            }
        }
        composeRule.onNodeWithTag(HOME_LOADING_TEST_TAG).assertIsDisplayed()

        composeRule.runOnIdle {
            uiState = HomeUiState(
                contentStatus = HomeContentStatus.Error(HomeInitialError.TopicNotFound),
            )
        }
        composeRule.onNodeWithTag(HOME_INITIAL_ERROR_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("오늘의 주제가 아직 준비되지 않았어요").assertIsDisplayed()

        composeRule.runOnIdle {
            uiState = contentUiState(photos = emptyList())
        }
        composeRule.onNodeWithTag(HOME_EMPTY_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("아직 올라온 사진이 없어요").assertIsDisplayed()
        composeRule.onNodeWithText("첫 번째 사진을 올려보세요").assertIsDisplayed()

        composeRule.runOnIdle {
            uiState = contentUiState()
        }
        composeRule.onNodeWithContentDescription("작품 이미지: 사진 0").assertIsDisplayed()
        composeRule.onNodeWithText("8월 28일 · 오늘의 주제").assertIsDisplayed()

        composeRule.runOnIdle {
            uiState = contentUiState(isLoadingNext = true)
        }
        composeRule.onNodeWithTag(HOME_NEXT_LOADING_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun genericErrorRefreshButtonIsAccessibleAndDispatchesRetry() {
        val actions = mutableListOf<HomeUiAction>()
        setHomeContent(
            uiState = HomeUiState(
                contentStatus = HomeContentStatus.Error(HomeInitialError.Generic),
            ),
            onAction = actions::add,
        )

        composeRule
            .onNodeWithContentDescription(HOME_REFRESH_CONTENT_DESCRIPTION)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("홈을 불러오지 못했어요").assertIsDisplayed()

        assertEquals(listOf(HomeUiAction.RetryClicked), actions)
    }

    @Test
    fun eachInitialErrorDisplaysItsApprovedMessage() {
        val cases = listOf(
            HomeInitialError.TopicNotFound to "오늘의 주제가 아직 준비되지 않았어요",
            HomeInitialError.Unauthorized to "로그인 정보를 확인할 수 없어요",
            HomeInitialError.Network to "네트워크 연결을 확인해 주세요",
            HomeInitialError.InvalidResponse to "홈 정보를 불러오지 못했어요",
            HomeInitialError.Client to "요청을 처리하지 못했어요",
            HomeInitialError.Server to "서버에 잠시 문제가 생겼어요",
            HomeInitialError.Generic to "홈을 불러오지 못했어요",
        )
        var uiState by mutableStateOf(
            HomeUiState(contentStatus = HomeContentStatus.Error(cases.first().first)),
        )
        composeRule.setContent {
            ChalkakTheme {
                HomeScreen(
                    uiState = uiState,
                    snackbarHostState = remember { SnackbarHostState() },
                    onAction = {},
                )
            }
        }

        cases.forEach { (error, message) ->
            composeRule.runOnIdle {
                uiState = HomeUiState(contentStatus = HomeContentStatus.Error(error))
            }
            composeRule.onNodeWithText(message).assertIsDisplayed()
        }
    }

    @Test
    fun pullToRefreshDispatchesRefreshAndBottomItemsDispatchSelection() {
        val actions = mutableListOf<HomeUiAction>()
        setHomeContent(
            uiState = contentUiState(photos = photos(12)),
            onAction = actions::add,
        )

        composeRule.onNode(hasScrollAction()).performTouchInput {
            swipe(
                start = center.copy(y = center.y * 0.25f),
                end = center.copy(y = center.y * 1.75f),
                durationMillis = 600,
            )
        }
        composeRule.waitUntil { HomeUiAction.RefreshRequested in actions }

        composeRule.onNodeWithText("오늘").performClick()
        composeRule.onNodeWithText("전시").performClick()

        assertEquals(1, actions.count { it == HomeUiAction.RefreshRequested })
        assertEquals(
            1,
            actions.count {
                it == HomeUiAction.BottomBarSelected(ChalkakBottomBarItem.TODAY)
            },
        )
        assertEquals(
            1,
            actions.count {
                it == HomeUiAction.BottomBarSelected(ChalkakBottomBarItem.DISPLAY)
            },
        )
    }

    @Test
    fun pageFailureHasNoRetryRowOrSnackbar() {
        val repository = PageFailureHomeRepository()
        val viewModel = HomeViewModel(
            repository = repository,
            sessionState = MutableStateFlow(UserSessionState.Authenticated("user-id")),
            dateProvider = { LocalDate.of(2026, 8, 28) },
        )
        composeRule.setContent {
            ChalkakTheme {
                HomeRoute(
                    onOpenPhotoUpload = {},
                    onNavigateToBottomBar = {},
                    viewModel = viewModel,
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToIndex(19)
        composeRule.waitUntil { repository.pageRequestCount == 1 }
        composeRule.onNodeWithTag(HOME_NEXT_LOADING_TEST_TAG).assertIsDisplayed()

        repository.pageResult.complete(HomeResult.Failure(HomeFailure.Network))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(HOME_NEXT_LOADING_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("작품 이미지: 사진 19").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(HOME_REFRESH_CONTENT_DESCRIPTION).assertCountEquals(0)
        composeRule.onAllNodesWithText(GUEST_LIKE_MESSAGE).assertCountEquals(0)
        composeRule.onAllNodesWithText("네트워크 연결을 확인해 주세요").assertCountEquals(0)
        composeRule.onAllNodesWithText(HOME_ERROR_MESSAGE).assertCountEquals(0)
    }

    @Test
    fun guestLikeSnackbarDoesNotDelayNavigationEvents() {
        val repository = GuestHomeRepository()
        var openPhotoUploadCount = 0
        var navigateToBottomBarCount = 0
        val viewModel = HomeViewModel(
            repository = repository,
            sessionState = MutableStateFlow(UserSessionState.Guest),
            dateProvider = { LocalDate.of(2026, 8, 28) },
        )
        composeRule.setContent {
            ChalkakTheme {
                HomeRoute(
                    onOpenPhotoUpload = { openPhotoUploadCount++ },
                    onNavigateToBottomBar = { navigateToBottomBarCount++ },
                    viewModel = viewModel,
                )
            }
        }

        composeRule.onNodeWithContentDescription("좋아요 17").performClick()

        composeRule.onAllNodesWithText(GUEST_LIKE_MESSAGE).assertCountEquals(1)
        assertEquals(0, repository.likeRequestCount)
        composeRule.onAllNodesWithText("로그인 없이 사진 둘러보기").assertCountEquals(0)
        composeRule.onAllNodesWithText("로그인").assertCountEquals(0)

        composeRule.onNodeWithContentDescription("추가").performClick()
        composeRule.onNodeWithText("전시").performClick()

        composeRule.waitUntil(timeoutMillis = 1_000) {
            openPhotoUploadCount == 1 && navigateToBottomBarCount == 1
        }
    }

    @Test
    fun endThresholdEmitsOnceUntilListLeavesAndReenters() {
        val thresholdEvents = mutableListOf<Boolean>()
        setHomeContent(
            uiState = contentUiState(photos = photos(20)),
            onAction = { action ->
                if (action is HomeUiAction.EndThresholdChanged) {
                    thresholdEvents += action.isReached
                }
            },
        )
        val photoList = composeRule.onNode(hasScrollAction())

        photoList.performScrollToIndex(19)
        composeRule.waitUntil { thresholdEvents.count { it } == 1 }

        photoList.performScrollToIndex(0)
        composeRule.waitUntil { thresholdEvents.lastOrNull() == false }

        photoList.performScrollToIndex(19)
        composeRule.waitUntil { thresholdEvents.count { it } == 2 }
    }

    private fun setHomeContent(
        uiState: HomeUiState,
        onAction: (HomeUiAction) -> Unit = {},
    ) {
        composeRule.setContent {
            ChalkakTheme {
                HomeScreen(
                    uiState = uiState,
                    snackbarHostState = remember { SnackbarHostState() },
                    onAction = onAction,
                )
            }
        }
    }
}

private fun contentUiState(
    photos: List<Post> = photos(1),
    isLoadingNext: Boolean = false,
) = HomeUiState(
    contentStatus = HomeContentStatus.Content,
    topicDate = LocalDate.of(2026, 8, 28),
    topic = "바다",
    photos = photos,
    selectedSort = PostSort.LATEST,
    currentPage = 1,
    hasNext = true,
    isLoadingNext = isLoadingNext,
)

private fun photos(count: Int) = List(count) { index ->
    Post(
        id = "photo-$index",
        imageUrl = "android.resource://com.stonefive.chalkak/drawable/preview_photo",
        signatureUrl = "android.resource://com.stonefive.chalkak/drawable/preview_signature",
        contentDescription = "작품 이미지: 사진 $index",
        title = "사진 $index",
        likeCount = 17,
    )
}

private class GuestHomeRepository : HomeRepository {
    var likeRequestCount = 0

    override suspend fun getHome(query: HomeQuery): HomeResult<PostContent> = HomeResult.Success(
        PostContent(
            topicDate = LocalDate.of(2026, 8, 28),
            topic = "바다",
            photos = photos(1),
            likedPhotoIds = emptySet(),
            currentPage = 1,
            hasNext = false,
        ),
    )

    override suspend fun getPostPage(query: HomeQuery): HomeResult<PostPage> = error("unused")

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): HomeResult<HomeLike> {
        likeRequestCount++
        return HomeResult.Success(HomeLike(likeCount = 18, isLiked = isLiked))
    }
}

private class PageFailureHomeRepository : HomeRepository {
    val pageResult = CompletableDeferred<HomeResult<PostPage>>()
    var pageRequestCount = 0

    override suspend fun getHome(query: HomeQuery): HomeResult<PostContent> = HomeResult.Success(
        PostContent(
            topicDate = LocalDate.of(2026, 8, 28),
            topic = "바다",
            photos = photos(20),
            likedPhotoIds = emptySet(),
            currentPage = 1,
            hasNext = true,
        ),
    )

    override suspend fun getPostPage(query: HomeQuery): HomeResult<PostPage> {
        pageRequestCount++
        return pageResult.await()
    }

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): HomeResult<HomeLike> = error("unused")
}
