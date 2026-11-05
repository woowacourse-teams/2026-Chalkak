package com.stonefive.chalkak.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FeedbackPushNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun feedbackPushReplacesExistingInquiryStackAndReturnsHome() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = Home) {
                composable<Home> {}
                composable<FeedbackList> {}
                composable<FeedbackDetail> {}
            }
        }
        composeRule.runOnIdle {
            navController.navigate(FeedbackList)
            navController.navigate(FeedbackDetail("old"))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            navController.openPushNotificationTarget(NotificationTarget.FeedbackInquiry("new"))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(
                "new",
                navController.currentBackStackEntry!!
                    .toRoute<FeedbackDetail>()
                    .feedbackId,
            )
            navController.popBackStack()
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(navController.currentDestination!!.hasRoute<FeedbackList>())
            navController.popBackStack()
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(navController.currentDestination!!.hasRoute<Home>())
        }
    }

    @Test
    fun feedbackPushWithoutExistingListCreatesListBelowDetail() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = Home) {
                composable<Home> {}
                composable<FeedbackList> {}
                composable<FeedbackDetail> {}
            }
        }
        composeRule.runOnIdle {
            navController.openPushNotificationTarget(NotificationTarget.FeedbackInquiry("new"))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { navController.popBackStack() }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(navController.currentDestination!!.hasRoute<FeedbackList>())
            navController.popBackStack()
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(navController.currentDestination!!.hasRoute<Home>())
        }
    }
}
