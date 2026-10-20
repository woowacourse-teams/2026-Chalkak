package com.stonefive.chalkak.feature.home

import android.graphics.Bitmap
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.stonefive.chalkak.feature.home.component.HOME_HERO_TEST_TAG
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
        assertEquals(ChalkakBottomBarItem.HOME, tab)
        screenshot("home-empty")
    }

    @Test
    fun validatingUploadShowsProcessingInsteadOfEmptySlot() {
        showHome(readyState().copy(hero = HomeHeroState.Processing))
        composeRule.onNodeWithText("사진을 처리하고 있어요").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("하늘 사진을 공유하지 않았어요").assertDoesNotExist()
    }

    @Test
    fun missingTopicShowsPreparationMessageAndHeroHasNoUploadAction() {
        showHome(readyState().copy(topic = "", hero = HomeHeroState.NoOpenTopic))
        composeRule.onNodeWithText("오늘의 주제가 아직 준비되지 않았어요").assertIsDisplayed()
        composeRule.onNodeWithText("오늘의 주제를 불러오지 못했어요").assertDoesNotExist()
        composeRule.onNodeWithText("지금은 참여할 수 있는 주제가 없어요").assertIsDisplayed()
        composeRule.onNodeWithTag(HOME_HERO_TEST_TAG).assertHasNoClickAction()
        composeRule.onNodeWithContentDescription("오늘의 주제 사진을 공유하지 않았어요").assertDoesNotExist()
    }

    @Test
    fun ownedHeroHasNoClickActionAndWeeklyCountIncludesPending() {
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
            .assertHasNoClickAction()
        composeRule.onNodeWithTag(HOME_HERO_TEST_TAG).assertHasNoClickAction()
        screenshot("home-owned")
        composeRule.onNodeWithTag(HOME_CONTENT_TEST_TAG).performScrollToIndex(3)
        composeRule.onNodeWithText("5/7").assertIsDisplayed()
        composeRule.onNodeWithText("금", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("토", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("일", useUnmergedTree = true).assertDoesNotExist()
        screenshot("home-weekly")
        composeRule.onNodeWithContentDescription("이번 주 기록 더보기").performClick()
        assertEquals(1, records)
    }

    @Test
    fun feedbackCardIsHiddenForPendingPhoto() {
        showHome(readyState().copy(hero = HomeHeroState.Photo(image(R.drawable.home_feed_photo), isPending = true)))
        composeRule.onNodeWithTag(HOME_CONTENT_TEST_TAG).performScrollToIndex(2)
        composeRule.onNodeWithContentDescription(FEEDBACK_READY_TITLE).assertDoesNotExist()
    }

    @Test
    fun readyFeedbackOpensStartSheetAndStartsAnalysis() {
        var starts = 0
        showHome(approvedState(), onStartFeedback = { starts++ })
        composeRule.onNodeWithTag(HOME_CONTENT_TEST_TAG).performScrollToIndex(2)
        composeRule.onNodeWithContentDescription(FEEDBACK_READY_TITLE).performClick()
        composeRule.onNodeWithText("AI 피드백을 시작할까요?").assertIsDisplayed()
        screenshot("home-feedback-sheet")
        composeRule.onNodeWithText("분석 시작하기").performClick()
        composeRule.waitForIdle()
        assertEquals(1, starts)
        composeRule.onNodeWithText("AI 피드백을 시작할까요?").assertDoesNotExist()
    }

    @Test
    fun processingFeedbackCannotBeOpened() {
        showHome(approvedState().copy(feedbackPhase = HomeFeedbackPhase.Processing))
        composeRule.onNodeWithTag(HOME_CONTENT_TEST_TAG).performScrollToIndex(2)
        composeRule
            .onNodeWithContentDescription("AI 피드백이 진행중이에요")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun completedFeedbackExpandsShowsTipAndCollapses() {
        showHome(approvedState().copy(feedbackPhase = HomeFeedbackPhase.Completed))
        composeRule.onNodeWithTag(HOME_CONTENT_TEST_TAG).performScrollToIndex(2)
        val header = composeRule.onNodeWithContentDescription("AI 피드백이 완료되었어요")
        header.performClick()
        composeRule.onNodeWithText(FEEDBACK_HEADLINE).assertIsDisplayed()
        screenshot("home-feedback-expanded")

        composeRule.onNodeWithText("작가님의 해석이 돋보이는 작품이에요").performClick()
        composeRule.onNodeWithText(FEEDBACK_TIP).assertIsDisplayed()
        screenshot("home-feedback-tip")
        composeRule.onNodeWithText("좋았던 점").performClick()
        composeRule.onNodeWithText(FEEDBACK_TIP).assertDoesNotExist()

        header.performClick()
        composeRule.onNodeWithText(FEEDBACK_HEADLINE).assertDoesNotExist()
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
    fun issueShowsOneBetaCardAndInvokesOpenAction() {
        var openedPage: Pair<String, String>? = null
        showHome(readyState(), onIssue = { title, url -> openedPage = title to url })
        composeRule.onNodeWithTag(HOME_CONTENT_TEST_TAG).performScrollToIndex(5)
        composeRule.onNodeWithText("ISSUE").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("함께 만들어주신 찰캌")
            .assertIsDisplayed()
            .performClick()
        composeRule
            .onNodeWithContentDescription("지난 주 가장 인기있었던 사진")
            .assertDoesNotExist()
        assertEquals("ISSUE" to "https://2026-chalkak-web.vercel.app/beta/beta.html", openedPage)
        screenshot("home-issues")
    }

    private fun showHome(
        state: HomeUiState,
        onUpload: () -> Unit = {},
        onRecord: () -> Unit = {},
        onDisplay: (LocalDate) -> Unit = {},
        onFeed: (Post) -> Unit = {},
        onTab: (ChalkakBottomBarItem) -> Unit = {},
        onIssue: (String, String) -> Unit = { _, _ -> },
        onStartFeedback: () -> Unit = {},
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
                    onStartFeedback = onStartFeedback,
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
private const val FEEDBACK_READY_TITLE = "이 사진, AI에게 피드백 받아보기"
private const val FEEDBACK_HEADLINE = "건물의 크기와 높이 차이가 잘 비교되는 구도예요"
private const val FEEDBACK_TIP = "구도 점수와 별개로, 주제 해석이 특별한 사진이라는 의미예요."

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

private fun approvedState() = readyState().copy(hero = HomeHeroState.Photo(image(R.drawable.home_feed_photo)))

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
