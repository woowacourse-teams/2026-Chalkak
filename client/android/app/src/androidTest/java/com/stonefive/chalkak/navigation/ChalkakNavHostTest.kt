package com.stonefive.chalkak.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.stonefive.chalkak.core.analytics.AnalyticsTracker
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChalkakNavHostTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displayOpenedFromRecordReturnsToRecordAndKeepsTabsNavigable() {
        lateinit var navController: NavHostController

        composeRule.setContent {
            navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = Today,
            ) {
                composable<Today> { Text("Today") }
                composable<Display> { Text("Display") }
                composable<Record> { Text("Record") }
                composable<Settings> { Text("Settings") }
            }
        }

        val selectedDate = LocalDate.of(2026, 8, 2)
        composeRule.runOnIdle {
            navController.navigate(Record)
            navController.navigateToDisplay(selectedDate)
        }

        composeRule.runOnIdle {
            assertEquals(
                selectedDate.toString(),
                navController.currentBackStackEntry
                    ?.toRoute<Display>()
                    ?.date,
            )
            assertTrue(navController.popBackStack())
            assertTrue(navController.currentDestination?.hasRoute<Record>() == true)

            navController.navigateToDisplay(selectedDate)
            navController.navigateToBottomBar(ChalkakBottomBarItem.RECORD, NoOpAnalyticsTracker)
            assertTrue(navController.currentDestination?.hasRoute<Record>() == true)

            navController.navigateToDisplay(selectedDate)

            navController.navigateToBottomBar(ChalkakBottomBarItem.SETTINGS, NoOpAnalyticsTracker)
            assertTrue(navController.currentDestination?.hasRoute<Settings>() == true)

            navController.navigateToBottomBar(ChalkakBottomBarItem.RECORD, NoOpAnalyticsTracker)
            assertTrue(navController.currentDestination?.hasRoute<Record>() == true)
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
