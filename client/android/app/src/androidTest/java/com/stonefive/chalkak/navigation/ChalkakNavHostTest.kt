package com.stonefive.chalkak.navigation

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.stonefive.chalkak.MainActivity
import com.stonefive.chalkak.core.analytics.AnalyticsTracker
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.home.HOME_CONTENT_TEST_TAG
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChalkakNavHostTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun notificationsOpenFromHomeAndBackReturnsToHome() {
        lateinit var navController: NavHostController
        composeRule.activity.setContent {
            navController = rememberNavController()
            ChalkakTheme {
                ChalkakNavHost(
                    analyticsTracker = NoOpAnalyticsTracker,
                    navController = navController,
                    startDestination = Home,
                )
            }
        }

        composeRule.onNodeWithContentDescription("알림").performClick()
        composeRule.onNodeWithText("9월 22일 오늘의 주제를 확인해보세요.").assertIsDisplayed()
        composeRule.onNodeWithText("어제 21:10").assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(navController.currentDestination?.hasRoute<Notifications>() == true)
        }

        composeRule.onNodeWithContentDescription("뒤로 가기").performClick()
        composeRule.runOnIdle {
            assertTrue(navController.currentDestination?.hasRoute<Home>() == true)
        }
    }

    @Test
    fun homeRankingLinksOpenPopularDisplayForTheirDates() {
        lateinit var navController: NavHostController
        composeRule.activity.setContent {
            navController = rememberNavController()
            ChalkakTheme {
                ChalkakNavHost(
                    analyticsTracker = NoOpAnalyticsTracker,
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
            navController = rememberNavController()
            ChalkakTheme {
                ChalkakNavHost(
                    analyticsTracker = NoOpAnalyticsTracker,
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
            navController = rememberNavController()
            ChalkakTheme {
                ChalkakNavHost(
                    analyticsTracker = NoOpAnalyticsTracker,
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
}

private object NoOpAnalyticsTracker : AnalyticsTracker {
    override fun trackScreenView(
        screenName: String,
        screenClass: String,
    ) = Unit

    override fun trackBottomNavigationSelection(destination: String) = Unit
}
