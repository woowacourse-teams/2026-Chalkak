package com.stonefive.chalkak.navigation

import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.stonefive.chalkak.MainActivity
import com.stonefive.chalkak.core.analytics.AnalyticsTracker
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChalkakNavHostTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun displayOpenedFromRecordReturnsToRecordAndKeepsTabsNavigable() {
        lateinit var navController: NavHostController

        composeRule.activity.setContent {
            navController = rememberNavController()
            ChalkakTheme {
                ChalkakNavHost(
                    analyticsTracker = NoOpAnalyticsTracker,
                    navController = navController,
                    startDestination = Today,
                )
            }
        }
        composeRule.waitForIdle()

        val selectedDate = LocalDate.of(2026, 8, 2)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<Today>() == true
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
                    ?.hasRoute<Today>() == true,
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
                    startDestination = Today,
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
                    ?.hasRoute<Today>() == true,
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
