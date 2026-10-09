package com.stonefive.chalkak.feature.home

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.platform.app.InstrumentationRegistry
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.domain.model.PostStatus
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyHeroOpensUploadAndBottomTabReadsHome() {
        var uploads = 0
        var tab: ChalkakBottomBarItem? = null
        showHome(readyState(), onUpload = { uploads++ }, onTab = { tab = it })
        composeRule
            .onNodeWithContentDescription("하늘 사진을 공유하지 않았어요")
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, uploads)
        composeRule
            .onNodeWithText("홈")
            .assertIsDisplayed()
            .performClick()
        assertEquals(ChalkakBottomBarItem.TODAY, tab)
        screenshot("home-empty")
    }

    @Test
    fun validatingUploadShowsProcessingInsteadOfEmptySlot() {
        showHome(readyState().copy(hero = HomeHeroState.Processing))
        composeRule.onNodeWithText("사진을 처리하고 있어요").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("하늘 사진을 공유하지 않았어요").assertDoesNotExist()
    }

    @Test
    fun ownedHeroShowsPhotoAndWeeklyCountIncludesPending() {
        var records = 0
        val state = readyState().copy(
            hero = HomeHeroState.Photo(
                image(R.drawable.home_feed_photo),
                signatureUrl = image(R.drawable.preview_signature),
            ),
            week = homeWeekDates(DATE).mapIndexed { index, day ->
                HomeWeekDay(
                    day,
                    if (index <
                        5
                    ) {
                        PostCalendarItem("week-$index", day, image(R.drawable.preview_photo), PostStatus.PENDING)
                    } else {
                        null
                    },
                )
            },
        )
        showHome(state, onRecord = { records++ })
        composeRule
            .onNodeWithContentDescription("오늘 제출한 사진")
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, records)
        screenshot("home-owned")
        composeRule.onNodeWithTag(HOME_CONTENT_TEST_TAG).performScrollToIndex(2)
        composeRule.onNodeWithText("5/7").assertIsDisplayed()
        composeRule.onNodeWithText("금", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("토", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("일", useUnmergedTree = true).assertDoesNotExist()
        screenshot("home-weekly")
        composeRule.onNodeWithContentDescription("이번 주 기록 더보기").performClick()
        assertEquals(2, records)
    }

    @Test
    fun pendingHeroDisplaysModerationBadge() {
        showHome(readyState().copy(hero = HomeHeroState.Photo(image(R.drawable.home_feed_photo), isPending = true)))
        composeRule.onNodeWithContentDescription("오늘 제출한 사진, 검수 대기").assertIsDisplayed()
        composeRule.onNodeWithText("검수 대기", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun rankingLinksOpenYesterdayAndTodayDates() {
        val dates = mutableListOf<LocalDate>()
        showHome(readyState(), onDisplay = dates::add)
        composeRule.onNodeWithTag(HOME_CONTENT_TEST_TAG).performScrollToIndex(3)
        composeRule
            .onNodeWithContentDescription("어제 랭킹 더보기")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(HOME_CONTENT_TEST_TAG).performScrollToIndex(4)
        composeRule
            .onNodeWithContentDescription("오늘 인기있는 사진 더보기")
            .assertIsDisplayed()
            .performClick()
        assertEquals(listOf(DATE.minusDays(1), DATE), dates)
        screenshot("home-rankings")
    }

    @Test
    fun rankingPhotosOpenTheirOwnFeedInsteadOfDisplay() {
        val selectedPosts = mutableListOf<Post>()
        val displayDates = mutableListOf<LocalDate>()
        val state = readyState().let { state ->
            state.copy(
                trending = state.trending.copy(
                    photos = state.trending.photos
                        .map { it.copy(contentDescription = "오늘 ${it.id}") },
                ),
            )
        }
        showHome(state, onDisplay = displayDates::add, onFeed = selectedPosts::add)
        composeRule.onNodeWithTag(HOME_CONTENT_TEST_TAG).performScrollToIndex(3)
        composeRule.onNodeWithContentDescription("랭킹 사진 0").performClick()
        composeRule.onNodeWithTag(HOME_CONTENT_TEST_TAG).performScrollToIndex(4)
        composeRule.onNodeWithContentDescription("오늘 photo-3").performClick()
        assertEquals(
            listOf(
                state.yesterday.photos
                    .first(),
                state.trending.photos
                    .first(),
            ),
            selectedPosts,
        )
        assertEquals(emptyList<LocalDate>(), displayDates)
    }

    @Test
    fun issuesShowTwoExampleCardsAndInvokePlaceholderAction() {
        var clicks = 0
        showHome(readyState(), onIssue = { clicks++ })
        composeRule.onNodeWithTag(HOME_CONTENT_TEST_TAG).performScrollToIndex(5)
        composeRule.onNodeWithText("ISSUE").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("바다에서 구도를 잘 잡는 방법")
            .assertIsDisplayed()
            .performClick()
        composeRule
            .onNodeWithContentDescription("지난 주 가장 인기있었던 사진")
            .assertIsDisplayed()
            .performClick()
        assertEquals(2, clicks)
        screenshot("home-issues")
    }

    private fun showHome(
        state: HomeUiState,
        onUpload: () -> Unit = {},
        onRecord: () -> Unit = {},
        onDisplay: (LocalDate) -> Unit = {},
        onFeed: (Post) -> Unit = {},
        onTab: (ChalkakBottomBarItem) -> Unit = {},
        onIssue: () -> Unit = {},
    ) {
        composeRule.setContent {
            ChalkakTheme {
                HomeScreen(
                    uiState = state,
                    onOpenPhotoUpload = onUpload,
                    onOpenRecord = onRecord,
                    onOpenDisplay = onDisplay,
                    onOpenFeed = onFeed,
                    onNavigateToBottomBar = onTab,
                    onRetry = {},
                    onNotificationClick = {},
                    onIssueClick = onIssue,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun screenshot(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "home-review")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

private val DATE = LocalDate.of(2026, 10, 8)

private fun readyState() = HomeUiState(
    date = DATE,
    topic = "하늘",
    topicStatus = HomeSectionStatus.Ready,
    hero = HomeHeroState.Empty,
    recordsStatus = HomeSectionStatus.Ready,
    yesterday = HomeRankingState(HomeSectionStatus.Ready, samplePhotos()),
    trending = HomeRankingState(HomeSectionStatus.Ready, samplePhotos().reversed()),
    isAuthenticated = true,
)

private fun samplePhotos(): List<Post> = List(4) { index ->
    Post(
        id = "photo-$index",
        originalImageUrl = image(R.drawable.preview_photo),
        thumbnailImageUrl = image(if (index % 2 == 0) R.drawable.preview_photo else R.drawable.home_feed_photo),
        signatureOriginalImageUrl = image(R.drawable.preview_signature),
        contentDescription = "랭킹 사진 $index",
        title = null,
        likeCount = 10 - index,
    )
}

private fun image(resourceId: Int) = "android.resource://com.stonefive.chalkak/$resourceId"
