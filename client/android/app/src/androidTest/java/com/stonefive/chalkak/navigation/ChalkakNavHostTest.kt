package com.stonefive.chalkak.navigation

import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.stonefive.chalkak.MainActivity
import com.stonefive.chalkak.core.analytics.AnalyticsTracker
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.home.HOME_CONTENT_TEST_TAG
import com.stonefive.chalkak.feature.home.HomeViewModel
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChalkakNavHostTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun issueWebPageTracksOneScreenViewOnEntry() {
        lateinit var navController: NavHostController
        val screenViews = mutableListOf<Pair<String, String>>()
        val analyticsTracker = object : AnalyticsTracker {
            override fun trackScreenView(
                screenName: String,
                screenClass: String,
            ) {
                screenViews += screenName to screenClass
            }

            override fun trackBottomNavigationSelection(destination: String) = Unit
        }

        composeRule.activity.setContent {
            val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
            navController = rememberNavController()
            ChalkakTheme {
                ChalkakNavHost(
                    analyticsTracker = analyticsTracker,
                    homeViewModel = homeViewModel,
                    navController = navController,
                    startDestination = Home,
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            navController.navigate(WebPage(title = "ISSUE", url = "about:blank"))
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            screenViews.count { it == ("issue" to "IssueWeb") } == 1
        }
        composeRule.runOnIdle {
            assertTrue(navController.currentDestination?.hasRoute<WebPage>() == true)
            assertEquals(1, screenViews.count { it == ("issue" to "IssueWeb") })
        }
    }

    @Test
    fun notificationsOpenFromHomeAndBackReturnsToHome() {
        lateinit var navController: NavHostController
        composeRule.activity.setContent {
            val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
            LaunchedEffect(homeViewModel) { homeViewModel.startPreloading() }
            navController = rememberNavController()
            ChalkakTheme {
                ChalkakNavHost(
                    analyticsTracker = NoOpAnalyticsTracker,
                    homeViewModel = homeViewModel,
                    navController = navController,
                    startDestination = Home,
                )
            }
        }

        composeRule.onNodeWithContentDescription("알림").performClick()
        composeRule.onNodeWithText("알림").assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(navController.currentDestination?.hasRoute<Notifications>() == true)
        }

        composeRule.onNodeWithContentDescription("뒤로 가기").performClick()
        composeRule.runOnIdle {
            assertTrue(navController.currentDestination?.hasRoute<Home>() == true)
        }
    }

    @Test
    fun repeatedBackClicksPopOnlyOneEntryAndBackWorksAfterTransition() {
        lateinit var navController: NavHostController
        composeRule.activity.setContent {
            val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
            navController = rememberNavController()
            ChalkakTheme {
                ChalkakNavHost(
                    analyticsTracker = NoOpAnalyticsTracker,
                    homeViewModel = homeViewModel,
                    navController = navController,
                    startDestination = Home,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { navController.navigate(Notifications) }
        composeRule.waitForIdle()
        val firstNotificationEntry = navController.currentBackStackEntry!!
        composeRule.runOnIdle { navController.navigate(Notifications) }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(
                Lifecycle.State.RESUMED,
                navController.currentBackStackEntry!!
                    .lifecycle.currentState,
            )
        }

        // Invoke the same rendered button twice before Compose can replace its callback.
        val backClick = composeRule
            .onNodeWithContentDescription("뒤로 가기")
            .fetchSemanticsNode()
            .config[SemanticsActions.OnClick]
            .action!!
        composeRule.runOnIdle {
            backClick()
            backClick()
            assertEquals(firstNotificationEntry.id, navController.currentBackStackEntry?.id)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(Lifecycle.State.RESUMED, firstNotificationEntry.lifecycle.currentState)
            // An old callback must stay harmless even after the previous screen resumes.
            backClick()
            assertEquals(firstNotificationEntry.id, navController.currentBackStackEntry?.id)
        }

        composeRule.onNodeWithContentDescription("뒤로 가기").performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(navController.currentDestination?.hasRoute<Home>() == true)
        }
    }

    @Test
    fun rapidHomeForwardClicksOnlyRunFirstRenderedCallback() {
        lateinit var navController: NavHostController
        composeRule.activity.setContent {
            val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
            LaunchedEffect(homeViewModel) { homeViewModel.startPreloading() }
            navController = rememberNavController()
            ChalkakTheme {
                ChalkakNavHost(
                    analyticsTracker = NoOpAnalyticsTracker,
                    homeViewModel = homeViewModel,
                    navController = navController,
                    startDestination = Home,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Home>() == true
        }

        val notificationClick = composeRule
            .onNodeWithContentDescription("알림")
            .fetchSemanticsNode()
            .config[SemanticsActions.OnClick]
            .action!!
        composeRule.onNodeWithTag(HOME_CONTENT_TEST_TAG).performScrollToIndex(4)
        val displayClick = composeRule
            .onNodeWithContentDescription("오늘 인기있는 사진 더보기")
            .fetchSemanticsNode()
            .config[SemanticsActions.OnClick]
            .action!!

        // Invoke different callbacks from the same Home composition before it can recompose.
        composeRule.runOnIdle {
            notificationClick()
            displayClick()
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(navController.currentDestination?.hasRoute<Notifications>() == true)
        }

        composeRule.onNodeWithContentDescription("뒤로 가기").performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(navController.currentDestination?.hasRoute<Home>() == true)
        }
    }

    @Test
    fun selectingHomeWhileAlreadyHomeKeepsTheSameEntry() {
        lateinit var navController: NavHostController
        composeRule.activity.setContent {
            val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
            navController = rememberNavController()
            ChalkakTheme {
                ChalkakNavHost(
                    analyticsTracker = NoOpAnalyticsTracker,
                    homeViewModel = homeViewModel,
                    navController = navController,
                    startDestination = Home,
                )
            }
        }
        composeRule.waitForIdle()
        val homeEntry = navController.currentBackStackEntry!!
        val homeClick = composeRule
            .onNode(hasText("홈") and hasClickAction())
            .fetchSemanticsNode()
            .config[SemanticsActions.OnClick]
            .action!!
        composeRule.runOnIdle {
            homeClick()
            homeClick()
            assertSame(homeEntry, navController.currentBackStackEntry)
            assertEquals(Lifecycle.State.RESUMED, homeEntry.lifecycle.currentState)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertSame(homeEntry, navController.currentBackStackEntry)
            assertEquals(null, navController.previousBackStackEntry)
        }
    }

    @Test
    fun rapidBottomTabSelectionsOnlyRunFirstRenderedCallbackAndTabsWorkAfterTransition() {
        lateinit var navController: NavHostController
        composeRule.activity.setContent {
            val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
            LaunchedEffect(homeViewModel) { homeViewModel.startPreloading() }
            navController = rememberNavController()
            ChalkakTheme {
                ChalkakNavHost(
                    analyticsTracker = NoOpAnalyticsTracker,
                    homeViewModel = homeViewModel,
                    navController = navController,
                    startDestination = Home,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Home>() == true
        }

        val settingsClick = composeRule
            .onNode(hasText("설정") and hasClickAction())
            .fetchSemanticsNode()
            .config[SemanticsActions.OnClick]
            .action!!
        val displayClick = composeRule
            .onNode(hasText("전시") and hasClickAction())
            .fetchSemanticsNode()
            .config[SemanticsActions.OnClick]
            .action!!

        // Both actions come from the Home bottom bar; only the first should survive.
        composeRule.runOnIdle {
            settingsClick()
            displayClick()
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(navController.currentDestination?.hasRoute<Settings>() == true)
        }

        composeRule.onNodeWithText("전시").performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(navController.currentDestination?.hasRoute<Display>() == true)
        }
    }

    @Test
    fun rapidSignatureSubmissionsOpenOnlyOnePreview() {
        lateinit var navController: NavHostController
        composeRule.activity.setContent {
            val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
            navController = rememberNavController()
            ChalkakTheme {
                ChalkakNavHost(
                    analyticsTracker = NoOpAnalyticsTracker,
                    homeViewModel = homeViewModel,
                    navController = navController,
                    startDestination = OnboardingSignature,
                )
            }
        }
        composeRule.onNodeWithTag("signaturePad").performTouchInput { click(center) }
        val submit = composeRule
            .onNodeWithTag("signatureSubmitButton")
            .fetchSemanticsNode()
            .config[SemanticsActions.OnClick]
            .action!!
        composeRule.runOnIdle {
            submit()
            submit()
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(navController.currentDestination?.hasRoute<OnboardingSignaturePreview>() == true)
            assertTrue(
                navController.previousBackStackEntry
                    ?.destination
                    ?.hasRoute<OnboardingSignature>() == true,
            )
        }
        composeRule.onNodeWithText("다시 그리기").performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(navController.currentDestination?.hasRoute<OnboardingSignature>() == true)
        }
    }

    @Test
    fun homeRankingLinksOpenPopularDisplayForTheirDates() {
        lateinit var navController: NavHostController
        composeRule.activity.setContent {
            val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
            LaunchedEffect(homeViewModel) { homeViewModel.startPreloading() }
            navController = rememberNavController()
            ChalkakTheme {
                ChalkakNavHost(
                    analyticsTracker = NoOpAnalyticsTracker,
                    homeViewModel = homeViewModel,
                    navController = navController,
                    startDestination = Home,
                )
            }
        }
        composeRule.waitForIdle()
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))

        composeRule.onNodeWithTag(HOME_CONTENT_TEST_TAG).performScrollToIndex(4)
        composeRule.onNodeWithContentDescription("오늘 인기있는 사진 더보기").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Display>() == true
        }
        composeRule.runOnIdle {
            val display = navController.currentBackStackEntry!!.toRoute<Display>()
            assertEquals(today.toString(), display.date)
            assertEquals("POPULAR", display.sort)
        }
        composeRule.onNodeWithText("홈").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Home>() == true
        }

        composeRule.onNodeWithTag(HOME_CONTENT_TEST_TAG).performScrollToIndex(3)
        composeRule.onNodeWithContentDescription("어제 랭킹 더보기").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Display>() == true
        }
        composeRule.runOnIdle {
            val display = navController.currentBackStackEntry!!.toRoute<Display>()
            assertEquals(today.minusDays(1).toString(), display.date)
            assertEquals("POPULAR", display.sort)
        }
        composeRule.onNodeWithText("홈").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Home>() == true
        }
        composeRule.onNodeWithText("설정").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Settings>() == true
        }
        composeRule.onNodeWithText("홈").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Home>() == true
        }
    }

    @Test
    fun displayOpenedFromRecordReturnsToRecordAndKeepsTabsNavigable() {
        lateinit var navController: NavHostController

        composeRule.activity.setContent {
            val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
            LaunchedEffect(homeViewModel) { homeViewModel.startPreloading() }
            navController = rememberNavController()
            ChalkakTheme {
                ChalkakNavHost(
                    analyticsTracker = NoOpAnalyticsTracker,
                    homeViewModel = homeViewModel,
                    navController = navController,
                    startDestination = Home,
                )
            }
        }
        composeRule.waitForIdle()

        val selectedDate = LocalDate.of(2026, 8, 2)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Home>() == true
        }
        composeRule.runOnIdle {
            navController.navigate(Record)
            navController.navigate(Display(date = selectedDate.toString()))
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Display>() == true
        }
        composeRule.runOnIdle {
            assertEquals(
                selectedDate.toString(),
                navController.currentBackStackEntry
                    ?.toRoute<Display>()
                    ?.date,
            )
            composeRule.activity
                .onBackPressedDispatcher
                .onBackPressed()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Record>() == true
        }

        composeRule.runOnIdle {
            navController.navigate(Display(date = selectedDate.toString()))
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Display>() == true
        }
        composeRule.onNodeWithText("설정").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Settings>() == true
        }
        composeRule.runOnIdle {
            assertTrue(
                navController.previousBackStackEntry
                    ?.destination
                    ?.hasRoute<Home>() == true,
            )
        }
    }

    @Test
    fun photoUploadSuccessConfirmOpensUploadedDateDisplayAndClearsUploadFlow() {
        lateinit var navController: NavHostController
        val uploadedDate = LocalDate.of(2026, 9, 22)

        composeRule.activity.setContent {
            val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
            LaunchedEffect(homeViewModel) { homeViewModel.startPreloading() }
            navController = rememberNavController()
            ChalkakTheme {
                ChalkakNavHost(
                    analyticsTracker = NoOpAnalyticsTracker,
                    homeViewModel = homeViewModel,
                    navController = navController,
                    startDestination = Home,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            navController.navigate(
                PhotoUploadSuccess(
                    imageModel = "uploaded-image",
                    caption = "업로드 사진",
                    date = uploadedDate.toString(),
                    topic = "오늘의 주제",
                    moderationStatus = "VALIDATING",
                ),
            )
        }

        composeRule.onNodeWithText("확인했어요.").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Display>() == true
        }
        composeRule.runOnIdle {
            assertEquals(
                uploadedDate.toString(),
                navController.currentBackStackEntry
                    ?.toRoute<Display>()
                    ?.date,
            )
            assertTrue(
                navController.previousBackStackEntry
                    ?.destination
                    ?.hasRoute<Home>() == true,
            )
        }
    }

    @Test
    fun topicNotificationWithoutNotificationIdOpensHomeFromOtherTab() {
        lateinit var navController: NavHostController

        composeRule.activity.setContent {
            val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
            LaunchedEffect(homeViewModel) { homeViewModel.startPreloading() }
            navController = rememberNavController()
            ChalkakTheme {
                ChalkakNavHost(
                    analyticsTracker = NoOpAnalyticsTracker,
                    homeViewModel = homeViewModel,
                    navController = navController,
                    startDestination = Home,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("설정").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Settings>() == true
        }

        composeRule.runOnIdle {
            navController.openNotificationTarget(
                type = "TOPIC",
                sourceType = "TOPIC",
                sourceId = "old-topic-id",
                notificationId = null,
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Home>() == true
        }
        composeRule.runOnIdle {
            assertFalse(navController.currentDestination?.hasRoute<Notifications>() == true)
            assertEquals(null, navController.previousBackStackEntry)
        }
    }

    @Test
    fun aiReviewCompletedNotificationOpensOwnedFeedWithResultIntent() {
        lateinit var navController: NavHostController

        composeRule.activity.setContent {
            val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
            LaunchedEffect(homeViewModel) { homeViewModel.startPreloading() }
            navController = rememberNavController()
            ChalkakTheme {
                ChalkakNavHost(
                    analyticsTracker = NoOpAnalyticsTracker,
                    homeViewModel = homeViewModel,
                    navController = navController,
                    startDestination = Home,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Home>() == true
        }

        composeRule.runOnIdle {
            navController.openNotificationTarget(
                type = "AI_REVIEW_COMPLETED",
                sourceType = "POST",
                sourceId = "post-id",
                notificationId = "notification-id",
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<FeedById>() == true
        }
        composeRule.runOnIdle {
            val route = navController.currentBackStackEntry!!.toRoute<FeedById>()
            assertEquals("post-id", route.postId)
            assertTrue(route.isOwnedByCurrentUser)
            assertTrue(route.openAiReviewResult)
            navController.openNotificationTarget(
                type = "AI_REVIEW_FAILED",
                sourceType = "POST",
                sourceId = "post-id",
                notificationId = "failed-notification-id",
            )
            val failedRoute = navController.currentBackStackEntry!!.toRoute<FeedById>()
            assertEquals("post-id", failedRoute.postId)
            assertTrue(failedRoute.openAiReviewResult)
            assertTrue(
                navController.previousBackStackEntry
                    ?.destination
                    ?.hasRoute<Home>() == true,
            )
        }
    }

    @Test
    fun aiReviewNotificationReplacesSamePostFeedOnTop() {
        lateinit var navController: NavHostController

        composeRule.activity.setContent {
            val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
            LaunchedEffect(homeViewModel) { homeViewModel.startPreloading() }
            navController = rememberNavController()
            ChalkakTheme {
                ChalkakNavHost(
                    analyticsTracker = NoOpAnalyticsTracker,
                    homeViewModel = homeViewModel,
                    navController = navController,
                    startDestination = Home,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Home>() == true
        }

        composeRule.runOnIdle {
            navController.navigate(
                Feed(
                    postId = "post-id",
                    originalImageUrl = "original",
                    thumbnailImageUrl = "thumbnail",
                    signatureOriginalImageUrl = "signature",
                    contentDescription = "사진",
                    title = null,
                    likeCount = 0,
                    dateLabel = "8월 28일의 주제",
                    topic = "하늘",
                    isOwnedByCurrentUser = true,
                ),
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Feed>() == true
        }

        composeRule.runOnIdle {
            navController.openNotificationTarget(
                type = "AI_REVIEW_COMPLETED",
                sourceType = "POST",
                sourceId = "post-id",
                notificationId = "notification-id",
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<FeedById>() == true
        }
        composeRule.runOnIdle {
            assertTrue(
                navController.currentBackStackEntry!!
                    .toRoute<FeedById>()
                    .openAiReviewResult,
            )
            assertTrue(
                navController.previousBackStackEntry
                    ?.destination
                    ?.hasRoute<Home>() == true,
            )
        }
    }

    @Test
    fun topicNotificationFromHomeDoesNotAddDuplicateHome() {
        lateinit var navController: NavHostController

        composeRule.activity.setContent {
            val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
            LaunchedEffect(homeViewModel) { homeViewModel.startPreloading() }
            navController = rememberNavController()
            ChalkakTheme {
                ChalkakNavHost(
                    analyticsTracker = NoOpAnalyticsTracker,
                    homeViewModel = homeViewModel,
                    navController = navController,
                    startDestination = Home,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Home>() == true
        }

        composeRule.runOnIdle {
            navController.openNotificationTarget(
                type = "TOPIC",
                sourceType = "TOPIC",
                sourceId = "old-topic-id",
                notificationId = null,
            )
            navController.openNotificationTarget(
                type = "TOPIC",
                sourceType = "TOPIC",
                sourceId = "old-topic-id",
                notificationId = null,
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Home>() == true
        }
        composeRule.runOnIdle {
            assertEquals(null, navController.previousBackStackEntry)
        }
    }
}

private object NoOpAnalyticsTracker : AnalyticsTracker {
    override fun trackScreenView(
        screenName: String,
        screenClass: String,
    ) = Unit

    override fun trackBottomNavigationSelection(destination: String) = Unit
}
